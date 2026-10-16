package co.gov.dane.edi.service;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.security.ReceiptProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** Radicado no reversible que reemplaza al C_C del PHP en la pantalla final (D17). */
@Service
public class ReceiptService {

  private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final int LENGTH = 10;

  private final byte[] key;
  private final SurveyProperties survey;

  public ReceiptService(ReceiptProperties properties, SurveyProperties survey) {
    this.key = properties.key().getBytes(StandardCharsets.UTF_8);
    this.survey = survey;
  }

  public String receiptFor(String respondentCode) {
    byte[] digest = hmac(survey.code() + ":" + survey.year() + ":" + respondentCode);
    StringBuilder code = new StringBuilder();
    int buffer = 0;
    int bits = 0;
    for (byte value : digest) {
      buffer = (buffer << 8) | (value & 0xFF);
      bits += 8;
      while (bits >= 5 && code.length() < LENGTH) {
        code.append(BASE32[(buffer >> (bits - 5)) & 31]);
        bits -= 5;
      }
      if (code.length() == LENGTH) {
        break;
      }
    }
    return code.substring(0, 5) + "-" + code.substring(5);
  }

  private byte[] hmac(String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("No fue posible calcular el radicado", exception);
    }
  }
}
