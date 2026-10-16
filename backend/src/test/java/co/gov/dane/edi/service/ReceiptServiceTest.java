package co.gov.dane.edi.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.security.ReceiptProperties;
import org.junit.jupiter.api.Test;

class ReceiptServiceTest {

  private final ReceiptService service =
      new ReceiptService(
          new ReceiptProperties("clave-de-radicado-de-prueba-0123456789"),
          new SurveyProperties("EDIN", 2026, "S", "/tmp"));

  @Test
  void receiptIsStableFormattedAndDoesNotRevealTheCode() {
    String receipt = service.receiptFor("1000000001");
    assertThat(receipt).matches("[A-Z2-7]{5}-[A-Z2-7]{5}").doesNotContain("1000000001");
    assertThat(service.receiptFor("1000000001")).isEqualTo(receipt);
  }

  @Test
  void differentRespondentsGetDifferentReceipts() {
    assertThat(service.receiptFor("1000000001")).isNotEqualTo(service.receiptFor("1000000002"));
  }

  @Test
  void receiptToStringOfPropertiesHidesTheKey() {
    assertThat(new ReceiptProperties("clave-secreta-de-radicado-0123456789").toString())
        .doesNotContain("clave-secreta");
  }

  @Test
  void receiptMatchesIndependentlyComputedKnownAnswer() {
    // Calculado aparte con Python: hmac sha256 + base32, 10 primeros caracteres (D17).
    assertThat(service.receiptFor("1000000001")).isEqualTo("VBDCT-2PSS6");
  }
}
