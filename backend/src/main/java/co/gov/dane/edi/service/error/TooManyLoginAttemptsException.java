package co.gov.dane.edi.service.error;

import java.time.Duration;

public class TooManyLoginAttemptsException extends RuntimeException {

  private final Duration retryAfter;

  public TooManyLoginAttemptsException(Duration retryAfter) {
    super("Demasiados intentos de inicio de sesión.");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
