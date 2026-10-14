package co.gov.dane.edi.security;

import co.gov.dane.edi.service.error.TooManyLoginAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Freno a la adivinación de claves. Es indispensable mientras las claves sigan en texto plano y con
 * 8 caracteres (decisión D7). En memoria porque el backend corre en una sola instancia (D6).
 *
 * <p>La memoria está acotada: nunca se registran más de {@code MAX_TRACKED_KEYS} claves. Las claves
 * bloqueadas no se desalojan, para que un bloqueo no pueda borrarse inventando usuarios. Bajo una
 * inundación de claves falsas, los contadores de claves no bloqueadas pueden reiniciarse o no
 * registrarse. La defensa real contra inundaciones es el límite de tasa en el proxy.
 */
@Component
public class LoginAttemptService {

  private static final int DEFAULT_MAX_TRACKED_KEYS = 10_000;
  private static final Duration PURGE_INTERVAL = Duration.ofMinutes(1);

  private final Map<String, AttemptWindow> attemptsByKey = new ConcurrentHashMap<>();
  private final Clock clock;
  private final int maxAttempts;
  private final Duration lockoutDuration;
  private final int maxTrackedKeys;
  private volatile Instant lastPurge = Instant.EPOCH;

  public LoginAttemptService(Clock clock, AppSecurityProperties properties) {
    this(clock, properties, DEFAULT_MAX_TRACKED_KEYS);
  }

  LoginAttemptService(Clock clock, AppSecurityProperties properties, int maxTrackedKeys) {
    this.clock = clock;
    this.maxAttempts = properties.maxLoginAttempts();
    this.lockoutDuration = Duration.ofMinutes(properties.lockoutMinutes());
    this.maxTrackedKeys = maxTrackedKeys;
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
    // Solo una clave nueva puede crecer el mapa; las existentes se actualizan sin revisar el tope.
    if (!attemptsByKey.containsKey(key) && !makeRoomFor(now)) {
      return;
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

  int trackedKeys() {
    return attemptsByKey.size();
  }

  /** Devuelve true si hay espacio para registrar una clave nueva. */
  private boolean makeRoomFor(Instant now) {
    if (attemptsByKey.size() < maxTrackedKeys) {
      return true;
    }
    if (!now.isBefore(lastPurge.plus(PURGE_INTERVAL))) {
      attemptsByKey.values().removeIf(window -> window.isExpired(now, lockoutDuration));
      lastPurge = now;
    }
    if (attemptsByKey.size() < maxTrackedKeys) {
      return true;
    }
    evictOldestUnlocked(now);
    return attemptsByKey.size() < maxTrackedKeys;
  }

  /** Desaloja las claves no bloqueadas más antiguas hasta quedar por debajo del 90 % del tope. */
  private void evictOldestUnlocked(Instant now) {
    int target = maxTrackedKeys * 9 / 10;
    List<Map.Entry<String, AttemptWindow>> candidates =
        attemptsByKey.entrySet().stream()
            .filter(entry -> !entry.getValue().isLocked(now))
            .sorted(
                (first, second) ->
                    first.getValue().lastFailure().compareTo(second.getValue().lastFailure()))
            .toList();
    for (Map.Entry<String, AttemptWindow> candidate : candidates) {
      if (attemptsByKey.size() < target) {
        return;
      }
      attemptsByKey.remove(candidate.getKey(), candidate.getValue());
    }
  }

  private record AttemptWindow(int failures, Instant lockedUntil, Instant lastFailure) {

    boolean isLocked(Instant now) {
      return lockedUntil != null && now.isBefore(lockedUntil);
    }

    boolean isExpired(Instant now, Duration window) {
      if (lockedUntil != null) {
        return !now.isBefore(lockedUntil);
      }
      return !now.isBefore(lastFailure.plus(window));
    }
  }
}
