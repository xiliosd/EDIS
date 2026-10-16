package co.gov.dane.edi.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Cifra el C_C dentro del JWT (D17): el token va firmado pero no cifrado, y quien copie la cookie
 * no debe poder leer la cédula. La clave se deriva del secreto JWT; rotarlo invalida también las
 * sesiones.
 */
@Component
public class SubjectCipher {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public SubjectCipher(AppSecurityProperties properties) {
    this.key = new SecretKeySpec(derive(properties.jwtSecret(), "edi-subject-v1"), "AES");
  }

  public String encrypt(String plain) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] output =
          ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
      return Base64.getUrlEncoder().withoutPadding().encodeToString(output);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("No fue posible cifrar el identificador", exception);
    }
  }

  public String decrypt(String token) {
    try {
      byte[] input = Base64.getUrlDecoder().decode(token);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, input, 0, IV_BYTES));
      byte[] plain = cipher.doFinal(input, IV_BYTES, input.length - IV_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalArgumentException("Identificador de sesión inválido", exception);
    }
  }

  static byte[] derive(String secret, String purpose) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("No fue posible derivar la clave", exception);
    }
  }
}
