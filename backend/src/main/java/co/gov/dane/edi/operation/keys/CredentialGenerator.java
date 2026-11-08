package co.gov.dane.edi.operation.keys;

import java.util.Objects;
import java.util.Random;

/**
 * Usuarios y claves de la muestra: 8 letras mayúsculas A-Z, el formato del login heredado y de las
 * cartas que se envían a los coordinadores. En producción recibe un {@link
 * java.security.SecureRandom}; las pruebas pasan un {@link Random} con semilla (Ruling 2).
 */
public class CredentialGenerator {

  static final int LENGTH = 8;

  private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private final Random random;

  public CredentialGenerator(Random random) {
    this.random = Objects.requireNonNull(random, "random");
  }

  /** Un valor nuevo de 8 letras; quien llama verifica que no esté en uso. */
  public String next() {
    char[] value = new char[LENGTH];
    for (int index = 0; index < LENGTH; index++) {
      value[index] = LETTERS.charAt(random.nextInt(LETTERS.length()));
    }
    return new String(value);
  }
}
