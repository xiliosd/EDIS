package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.service.error.InvalidEditException;
import co.gov.dane.edi.util.QuestionTextSanitizer;
import org.junit.jupiter.api.Test;

/** Forma guardada de los textos administrables y límite por columna real (Latin-9, en bytes). */
class AdminTextNormalizationTest {

  @Test
  void limitsComeFromTheRealColumns() {
    assertThat(EditableText.QUESTION.maxBytes()).isEqualTo(1000);
    assertThat(EditableText.HEADER.maxBytes()).isEqualTo(500);
    assertThat(EditableText.HELP_WORD.maxBytes()).isEqualTo(100);
  }

  @Test
  void storesOutsideLatin9AsNumericEntitiesAndReadsBackTheSameText() {
    String typed = "“Gestión” – año <b> & 😀 €";

    String stored = QuestionAdminService.normalize(typed, EditableText.QUESTION);

    assertThat(stored).isEqualTo("&#8220;Gestión&#8221; &#8211; año &lt;b&gt; &amp; &#128512; €");
    assertThat(QuestionTextSanitizer.toPlainText(stored)).isEqualTo(typed);
  }

  @Test
  void eachFieldAcceptsExactlyItsLimitInLatin9Bytes() {
    // ñ ocupa dos bytes en UTF-8 pero uno en Latin-9: el límite es en caracteres de Latin-9.
    assertThat(QuestionAdminService.normalize("ñ".repeat(1000), EditableText.QUESTION))
        .hasSize(1000);
    assertThat(QuestionAdminService.normalize("é".repeat(500), EditableText.HEADER)).hasSize(500);
    assertThat(QuestionAdminService.normalize("a".repeat(100), EditableText.HELP_WORD))
        .hasSize(100);
  }

  @Test
  void rejectsOneMoreThanTheLimitWithTheFieldMessage() {
    assertThatThrownBy(
            () -> QuestionAdminService.normalize("a".repeat(1001), EditableText.QUESTION))
        .isInstanceOf(InvalidEditException.class)
        .hasMessageStartingWith("El texto de la pregunta admite hasta 1000 caracteres");
    assertThatThrownBy(() -> QuestionAdminService.normalize("a".repeat(501), EditableText.HEADER))
        .hasMessageStartingWith("El encabezado admite hasta 500 caracteres");
    assertThatThrownBy(
            () -> QuestionAdminService.normalize("a".repeat(101), EditableText.HELP_WORD))
        .hasMessageStartingWith("La palabra de ayuda admite hasta 100 caracteres");
  }

  @Test
  void entitiesAndEscapesCountWithAllTheirCharacters() {
    // 94 caracteres escritos, pero la raya se guarda como "&#8211;" (7): justo 100.
    String fits = "a".repeat(93) + "–";
    assertThat(QuestionAdminService.normalize(fits, EditableText.HELP_WORD)).hasSize(100);
    assertThatThrownBy(() -> QuestionAdminService.normalize(fits + "a", EditableText.HELP_WORD))
        .isInstanceOf(InvalidEditException.class);
    // "&" se guarda como "&amp;" (5).
    assertThatThrownBy(() -> QuestionAdminService.normalize("&".repeat(21), EditableText.HELP_WORD))
        .isInstanceOf(InvalidEditException.class);
  }

  @Test
  void blankTextIsRejected() {
    assertThatThrownBy(() -> QuestionAdminService.normalize(" \u0007 ", EditableText.HEADER))
        .hasMessage("El texto no puede quedar vacío.");
  }
}
