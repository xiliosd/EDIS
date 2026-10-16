package co.gov.dane.edi.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Detiene el arranque fuera de dev y test si JWT_SECRET o RECEIPT_KEY conservan el valor de ejemplo
 * de .env.example: cumplen el largo mínimo, pero son públicos en el repositorio.
 */
@Component
public class PlaceholderSecretsGuard {

  /** Valores de .env.example; deben cambiar a la par con ese archivo. */
  static final String EXAMPLE_JWT_SECRET = "genere-un-secreto-aleatorio-de-al-menos-32-caracteres";

  static final String EXAMPLE_RECEIPT_KEY = "genere-otra-clave-aleatoria-de-al-menos-32-caracteres";

  private static final Profiles EXEMPT_PROFILES = Profiles.of("dev", "test");

  public PlaceholderSecretsGuard(
      Environment environment, AppSecurityProperties security, ReceiptProperties receipt) {
    check(environment, security.jwtSecret(), receipt.key());
  }

  static void check(Environment environment, String jwtSecret, String receiptKey) {
    if (environment.acceptsProfiles(EXEMPT_PROFILES)) {
      return;
    }
    if (EXAMPLE_JWT_SECRET.equals(jwtSecret)) {
      throw new IllegalStateException(
          "JWT_SECRET tiene el valor de ejemplo de .env.example; genere uno propio.");
    }
    if (EXAMPLE_RECEIPT_KEY.equals(receiptKey)) {
      throw new IllegalStateException(
          "RECEIPT_KEY tiene el valor de ejemplo de .env.example; genere una propia.");
    }
  }
}
