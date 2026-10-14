package co.gov.dane.edi.security;

import co.gov.dane.edi.service.error.TooManyLoginAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Freno a la adivinación de claves. Es indispensable mientras las claves sigan en texto plano y con
 * 8 caracteres (decisión D7). En memoria porque el backend corre en una sola instancia (D6).
 */
@Component
public class LoginAttemptService {

  // Evita que un atacante agote la memoria inventando usuarios.
  private static final int MAX_TRACKED_KEYS = 10_000;

  private final Map<String, AttemptWindow> attemptsByKey = new ConcurrentHashMap<>();
  private final Clock clock;
  private final int maxAttempts;
  private final Duration lockoutDuration;

  public LoginAttemptService(Clock clock, AppSecurityProperties properties) {
    this.clock = clock;
    this.maxAttempts = properties.maxLoginAttempts();
    this.lockoutDuration = Duration.ofMinutes(properties.lockoutMinutes());
  }

  public static String keyFor(String username, String clientAddress) {
    return username.strip().toUpperCase(Locale.ROOT) + "|" + clientAddress;
  }

  public void ensureNotLocked(String key) {
    AttemptWindow window = attemptsByKey.get(key);
    if (window == null || window.lockedUntil() == null) {
      return;
    }
    Instant now = clock.instant();
    if (now.isBefore(window.lockedUntil())) {
      throw new TooManyLoginAttemptsException(Duration.between(now, window.lockedUntil()));
    }
    attemptsByKey.remove(key, window);
  }

  public void recordFailure(String key) {
    Instant now = clock.instant();
    if (attemptsByKey.size() >= MAX_TRACKED_KEYS) {
      attemptsByKey.values().removeIf(window -> window.isExpired(now, lockoutDuration));
    }
    attemptsByKey.compute(
        key,
        (ignored, current) -> {
          int failures =
              current == null || current.isExpired(now, lockoutDuration)
                  ? 1
                  : current.failures() + 1;
          Instant lockedUntil = failures >= maxAttempts ? now.plus(lockoutDuration) : null;
          return new AttemptWindow(failures, lockedUntil, now);
        });
  }

  public void recordSuccess(String key) {
    attemptsByKey.remove(key);
  }

  private record AttemptWindow(int failures, Instant lockedUntil, Instant lastFailure) {

    boolean isExpired(Instant now, Duration window) {
      if (lockedUntil != null) {
        return !now.isBefore(lockedUntil);
      }
      return !now.isBefore(lastFailure.plus(window));
    }
  }
}
