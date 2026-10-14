package co.gov.dane.edi.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AppSecurityPropertiesTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();
  private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

  @Test
  void acceptsValidConfiguration() {
    assertThat(VALIDATOR.validate(properties(VALID_SECRET, 30))).isEmpty();
  }

  @Test
  void rejectsSecretShorterThan256Bits() {
    assertThat(VALIDATOR.validate(properties("corto", 30)))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("jwtSecret");
  }

  @Test
  void rejectsSessionsShorterThanFiveMinutes() {
    assertThat(VALIDATOR.validate(properties(VALID_SECRET, 1)))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("sessionMinutes");
  }

  @Test
  void exposesSessionDuration() {
    assertThat(properties(VALID_SECRET, 30).sessionDuration()).isEqualTo(Duration.ofMinutes(30));
  }

  private static AppSecurityProperties properties(String secret, int minutes) {
    return new AppSecurityProperties(secret, minutes, true, "http://localhost:5173", 5, 15, false);
  }
}
