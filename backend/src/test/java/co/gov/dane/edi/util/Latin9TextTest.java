package co.gov.dane.edi.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Latin9TextTest {

  @Test
  void keepsAsciiAsIs() {
    String ascii = "Pregunta 1: O'Brien & <b> {fn x} \\ /";

    assertThat(Latin9Text.encode(ascii)).isEqualTo(ascii);
    assertThat(Latin9Text.storedLength(ascii)).isEqualTo(ascii.length());
  }

  @Test
  void keepsLatin9LettersAndSymbolsAsIs() {
    String latin9 = "¿Año de gestión? ¡Pingüino! Ñandú €5 Œuvre Šalát";

    assertThat(Latin9Text.encode(latin9)).isEqualTo(latin9);
    // Un byte por carácter en WE8ISO8859P15, aunque en UTF-8 ocupen dos o tres.
    assertThat(Latin9Text.storedLength(latin9)).isEqualTo(latin9.length());
  }

  @Test
  void convertsTypographicQuotesAndDashesToNumericEntities() {
    String typed = "“comillas” ‘simples’ – raya — larga… • viñeta";

    assertThat(Latin9Text.encode(typed))
        .isEqualTo(
            "&#8220;comillas&#8221; &#8216;simples&#8217; &#8211; raya &#8212; larga&#8230;"
                + " &#8226; viñeta");
  }

  @Test
  void convertsCharactersThatLatin1HasButLatin9DoesNot() {
    // ISO-8859-15 reemplazó ¤ ¦ ¨ ´ ¸ ¼ ½ ¾ de Latin-1 por € Š š Ž ž Œ œ Ÿ.
    assertThat(Latin9Text.encode("¤¦¨´¸¼½¾"))
        .isEqualTo("&#164;&#166;&#168;&#180;&#184;&#188;&#189;&#190;");
  }

  @Test
  void convertsAnEmojiSurrogatePairToASingleEntity() {
    assertThat(Latin9Text.encode("Hola 😀")).isEqualTo("Hola &#128512;");
    assertThat(Latin9Text.storedLength("😀")).isEqualTo("&#128512;".length());
  }

  @Test
  void loneSurrogatesAreConvertedToo() {
    assertThat(Latin9Text.encode("a\uD83Db")).isEqualTo("a&#55357;b");
  }

  @Test
  void textJustAtTheLimitFits() {
    String text = "ñ".repeat(98) + "€é";

    assertThat(Latin9Text.storedLength(text)).isEqualTo(100);
    assertThat(Latin9Text.fits(text, 100)).isTrue();
  }

  @Test
  void entitiesCanPushATextOverTheLimit() {
    // 94 caracteres caben de sobra, pero la comilla tipográfica se guarda como 7 ("&#8220;").
    String text = "a".repeat(93) + "“";

    assertThat(text.length()).isEqualTo(94);
    assertThat(Latin9Text.storedLength(text)).isEqualTo(100);
    assertThat(Latin9Text.fits(text, 100)).isTrue();
    assertThat(Latin9Text.fits(text + "a", 100)).isFalse();
    assertThat(Latin9Text.fits("a".repeat(94) + "“", 100)).isFalse();
  }

  @Test
  void decodesNumericReferencesIncludingSupplementaryOnes() {
    assertThat(Latin9Text.decodeNumericReferences("&#8220;Hola&#8221; &#128512; &#x2014; &#X41;"))
        .isEqualTo("“Hola” 😀 — A");
  }

  /** Formas no canónicas que puede haber guardado el PHP: ceros a la izquierda y Latin-9. */
  @Test
  void decodesLeadingZerosAndLatin9CharactersStoredAsReferences() {
    assertThat(Latin9Text.decodeNumericReferences("&#08220;x&#x0201D; Caf&#233; &#8364;"))
        .isEqualTo("“x” Café €");
  }

  @Test
  void leavesNamedAndInvalidReferencesUntouched() {
    assertThat(Latin9Text.decodeNumericReferences("&amp; &#; &#xZZ; &#1114112; &#55357; a&#65"))
        .isEqualTo("&amp; &#; &#xZZ; &#1114112; &#55357; a&#65");
  }

  @Test
  void encodeThenDecodeRoundTrips() {
    String text = "“Gestión” – año 2026… 😀 € ñ";

    assertThat(Latin9Text.decodeNumericReferences(Latin9Text.encode(text))).isEqualTo(text);
  }

  @Test
  void answerKeepsALiteralNumericReferenceTypedByTheRespondent() {
    String typed = "Escribí &#8220; a mano y “esta” de verdad & fin";

    String stored = Latin9Text.encodeAnswer(typed);

    assertThat(stored).isEqualTo("Escribí &#38;#8220; a mano y &#8220;esta&#8221; de verdad & fin");
    assertThat(Latin9Text.decodeNumericReferences(stored)).isEqualTo(typed);
    assertThat(Latin9Text.encodeAnswer("sin cambios & ñ")).isEqualTo("sin cambios & ñ");
    assertThat(Latin9Text.encodeAnswer(null)).isNull();
  }

  @Test
  void handlesNull() {
    assertThat(Latin9Text.encode(null)).isNull();
    assertThat(Latin9Text.decodeNumericReferences(null)).isNull();
    assertThat(Latin9Text.storedLength(null)).isZero();
  }
}
