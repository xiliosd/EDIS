package co.gov.dane.edi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PlaceholderSecretsGuardTest {

  private static final String OWN_SECRET = "secreto-propio-de-prueba-0123456789abcdef";

  private static MockEnvironment environment(String... profiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    return environment;
  }

  @Test
  void rejectsTheExampleJwtSecretOutsideDevAndTest() {
    assertThatThrownBy(
            () ->
                PlaceholderSecretsGuard.check(
                    environment("prod"), PlaceholderSecretsGuard.EXAMPLE_JWT_SECRET, OWN_SECRET))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void rejectsTheExampleReceiptKeyWithoutProfiles() {
    assertThatThrownBy(
            () ->
                PlaceholderSecretsGuard.check(
                    environment(), OWN_SECRET, PlaceholderSecretsGuard.EXAMPLE_RECEIPT_KEY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RECEIPT_KEY");
  }

  @Test
  void devAndTestProfilesMayKeepTheExampleValues() {
    for (String profile : new String[] {"dev", "test"}) {
      assertThatCode(
              () ->
                  PlaceholderSecretsGuard.check(
                      environment(profile),
                      PlaceholderSecretsGuard.EXAMPLE_JWT_SECRET,
                      PlaceholderSecretsGuard.EXAMPLE_RECEIPT_KEY))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void ownSecretsAreAccepted() {
    assertThatCode(() -> new PlaceholderSecretsGuard(environment("prod"), security(), receipt()))
        .doesNotThrowAnyException();
  }

  @Test
  void constantsMatchTheEnvExampleFile() throws Exception {
    // Solo verifica que las constantes no se desalineen del archivo; el arranque no lo lee.
    assertThat(Files.readAllLines(Path.of("..", ".env.example")))
        .contains("JWT_SECRET=" + PlaceholderSecretsGuard.EXAMPLE_JWT_SECRET)
        .contains("RECEIPT_KEY=" + PlaceholderSecretsGuard.EXAMPLE_RECEIPT_KEY);
  }

  private static AppSecurityProperties security() {
    return new AppSecurityProperties(OWN_SECRET, 30, true, "http://localhost:5173", 5, 15, false);
  }

  private static ReceiptProperties receipt() {
    return new ReceiptProperties(OWN_SECRET);
  }
}
