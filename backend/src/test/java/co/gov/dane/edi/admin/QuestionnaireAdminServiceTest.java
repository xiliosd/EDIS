package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.admin.dto.AdminHelp;
import co.gov.dane.edi.admin.dto.AdminHelpDefinition;
import org.junit.jupiter.api.Test;

class QuestionnaireAdminServiceTest {

  @Test
  void helpDefinitionIsShownAsPlainText() {
    AdminHelp help =
        QuestionnaireAdminService.toPlainHelp(
            new AdminHelp(
                7L,
                501L,
                "desempe&ntilde;a",
                new AdminHelpDefinition(
                    "Desempe&ntilde;o", "<p>Texto <b>con</b> &lt;etiquetas&gt;.</p>")));

    assertThat(help.codPreAyu()).isEqualTo(7L);
    assertThat(help.codAyu()).isEqualTo(501L);
    assertThat(help.text()).isEqualTo("desempeña");
    assertThat(help.definition())
        .isEqualTo(new AdminHelpDefinition("Desempeño", "Texto con <etiquetas>."));
  }

  @Test
  void helpWithoutDefinitionKeepsItNull() {
    AdminHelp help =
        QuestionnaireAdminService.toPlainHelp(new AdminHelp(7L, 9999L, "palabra", null));

    assertThat(help.definition()).isNull();
  }

  @Test
  void longDefinitionIsCutWithoutSplittingCharacters() {
    String longText = "a".repeat(QuestionnaireAdminService.HELP_EXCERPT_LENGTH - 1) + "😀 resto";

    String excerpt = QuestionnaireAdminService.excerpt(longText);

    assertThat(excerpt).endsWith("😀…");
    assertThat(excerpt.codePointCount(0, excerpt.length()))
        .isEqualTo(QuestionnaireAdminService.HELP_EXCERPT_LENGTH + 1);
    assertThat(QuestionnaireAdminService.excerpt("corto")).isEqualTo("corto");
  }
}
