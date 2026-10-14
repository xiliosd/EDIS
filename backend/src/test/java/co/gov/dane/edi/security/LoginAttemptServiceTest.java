package co.gov.dane.edi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.service.error.TooManyLoginAttemptsException;
import co.gov.dane.edi.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

  private static final String KEY = LoginAttemptService.keyFor("PRUEBA01", "10.0.0.1");

  private MutableClock clock;
  private LoginAttemptService service;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-10-14T12:00:00Z"));
    AppSecurityProperties properties =
        new AppSecurityProperties(
            "0123456789abcdef0123456789abcdef", 30, true, "http://localhost", 5, 15, false);
    service = new LoginAttemptService(clock, properties);
  }

  @Test
  void allowsAttemptsBelowTheLimit() {
    failTimes(4);
    assertThatCode(() -> service.ensureNotLocked(KEY)).doesNotThrowAnyException();
  }

  @Test
  void locksAfterTheFifthFailureForFifteenMinutes() {
    failTimes(5);
    assertThatThrownBy(() -> service.ensureNotLocked(KEY))
        .isInstanceOfSatisfying(
            TooManyLoginAttemptsException.class,
            e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMinutes(15)));
  }

  @Test
  void unlocksAndResetsCounterWhenLockoutExpires() {
    failTimes(5);
    clock.advance(Duration.ofMinutes(15));
    assertThatCode(() -> service.ensureNotLocked(KEY)).doesNotThrowAnyException();
    service.recordFailure(KEY);
    assertThatCode(() -> service.ensureNotLocked(KEY)).doesNotThrowAnyException();
  }

  @Test
  void forgetsOldFailuresOutsideTheWindow() {
    failTimes(4);
    clock.advance(Duration.ofMinutes(16));
    service.recordFailure(KEY);
    assertThatCode(() -> service.ensureNotLocked(KEY)).doesNotThrowAnyException();
  }

  @Test
  void successClearsPreviousFailures() {
    failTimes(4);
    service.recordSuccess(KEY);
    failTimes(4);
    assertThatCode(() -> service.ensureNotLocked(KEY)).doesNotThrowAnyException();
  }

  @Test
  void keysAreIndependentPerAddress() {
    failTimes(5);
    String otherAddress = LoginAttemptService.keyFor("PRUEBA01", "10.0.0.2");
    assertThatCode(() -> service.ensureNotLocked(otherAddress)).doesNotThrowAnyException();
  }

  @Test
  void keyNormalizesUsernameCaseAndSpaces() {
    assertThat(LoginAttemptService.keyFor("  prueba01 ", "10.0.0.1")).isEqualTo(KEY);
  }

  @Test
  void unknownKeyIsNotLocked() {
    assertThatCode(() -> service.ensureNotLocked("DESCONOCIDA|10.0.0.7"))
        .doesNotThrowAnyException();
  }

  @Test
  void failureDuringLockExtendsIt() {
    failTimes(5);
    clock.advance(Duration.ofMinutes(10));
    service.recordFailure(KEY);
    assertThatThrownBy(() -> service.ensureNotLocked(KEY))
        .isInstanceOfSatisfying(
            TooManyLoginAttemptsException.class,
            e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMinutes(15)));
  }

  @Test
  void recordSuccessUnlocksLockedKey() {
    failTimes(5);
    service.recordSuccess(KEY);
    assertThatCode(() -> service.ensureNotLocked(KEY)).doesNotThrowAnyException();
  }

  @Test
  void capKeepsMapBoundedAndNeverEvictsLockedKeys() {
    LoginAttemptService bounded = new LoginAttemptService(clock, defaultProperties(), 20);
    String lockedKey = LoginAttemptService.keyFor("BLOQUEADO", "10.0.0.9");
    for (int attempt = 0; attempt < 5; attempt++) {
      bounded.recordFailure(lockedKey);
    }
    for (int user = 0; user < 200; user++) {
      bounded.recordFailure(LoginAttemptService.keyFor("USUARIO" + user, "10.0.0.1"));
    }
    assertThat(bounded.trackedKeys()).isLessThanOrEqualTo(20);
    assertThatThrownBy(() -> bounded.ensureNotLocked(lockedKey))
        .isInstanceOf(TooManyLoginAttemptsException.class);
  }

  private static AppSecurityProperties defaultProperties() {
    return new AppSecurityProperties(
        "0123456789abcdef0123456789abcdef", 30, true, "http://localhost", 5, 15, false);
  }

  private void failTimes(int times) {
    for (int attempt = 0; attempt < times; attempt++) {
      service.recordFailure(KEY);
    }
  }
}
