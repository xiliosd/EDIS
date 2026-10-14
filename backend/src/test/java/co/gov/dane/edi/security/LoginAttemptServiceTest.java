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

  private void failTimes(int times) {
    for (int attempt = 0; attempt < times; attempt++) {
      service.recordFailure(KEY);
    }
  }
}
