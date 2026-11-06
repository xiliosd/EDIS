package co.gov.dane.edi.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuestionTextSanitizerTest {

  @Test
  void decodesLegacyHtmlEntities() {
    assertThat(QuestionTextSanitizer.toPlainText("Gesti&oacute;n del a&ntilde;o"))
        .isEqualTo("Gestión del año");
  }

  @Test
  void removesTagsAndKeepsLineBreaks() {
    assertThat(QuestionTextSanitizer.toPlainText("<b>Uno</b><br>Dos <i>tres</i>"))
        .isEqualTo("Uno\nDos tres");
  }

  @Test
  void normalizesNonBreakingAndRepeatedSpaces() {
    assertThat(QuestionTextSanitizer.toPlainText("  A&nbsp;&nbsp;  B  ")).isEqualTo("A B");
  }

  @Test
  void keepsEscapedMarkupAsLiteralText() {
    assertThat(QuestionTextSanitizer.toPlainText("&lt;script&gt;alert(1)&lt;/script&gt;"))
        .isEqualTo("<script>alert(1)</script>");
  }

  @Test
  void decodesNumericEntitiesStoredForCharactersOutsideLatin9() {
    assertThat(QuestionTextSanitizer.toPlainText("&#8220;Gesti&oacute;n&#8221; &#8211; &#128512;"))
        .isEqualTo("“Gestión” – 😀");
    assertThat(QuestionTextSanitizer.toPlainText("&amp;#8220; literal"))
        .isEqualTo("&#8220; literal");
  }

  @Test
  void returnsEmptyTextForNull() {
    assertThat(QuestionTextSanitizer.toPlainText(null)).isEmpty();
  }
}
