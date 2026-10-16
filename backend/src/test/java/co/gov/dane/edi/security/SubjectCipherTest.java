package co.gov.dane.edi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubjectCipherTest {

  private static AppSecurityProperties properties(String secret) {
    return new AppSecurityProperties(secret, 30, true, "http://localhost", 5, 15, false);
  }

  private final SubjectCipher cipher =
      new SubjectCipher(properties("0123456789abcdef0123456789abcdef"));

  @Test
  void roundTripsWithoutRevealingThePlainValue() {
    String token = cipher.encrypt("1000000001");
    assertThat(token).doesNotContain("1000000001").matches("[A-Za-z0-9_-]+");
    assertThat(cipher.decrypt(token)).isEqualTo("1000000001");
  }

  @Test
  void sameValueEncryptsDifferentlyEachTime() {
    assertThat(cipher.encrypt("1000000001")).isNotEqualTo(cipher.encrypt("1000000001"));
  }

  @Test
  void tokenFromAnotherSecretIsRejected() {
    String foreign =
        new SubjectCipher(properties("ffffffffffffffffffffffffffffffff")).encrypt("1000000001");
    assertThatThrownBy(() -> cipher.decrypt(foreign)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void malformedTokenIsRejected() {
    assertThatThrownBy(() -> cipher.decrypt("no es base64 válido!"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cipher.decrypt("AAAA")).isInstanceOf(IllegalArgumentException.class);
  }
}
