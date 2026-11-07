package co.gov.dane.edi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import co.gov.dane.edi.admin.dto.AdminHeader;
import co.gov.dane.edi.model.AnswerOption;
import co.gov.dane.edi.model.HeaderKind;
import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class QuestionnaireRepositoryIT extends OracleIntegrationTest {

  @Autowired private QuestionnaireRepository questionnaire;
  @Autowired private AnswerColumnRepository answerColumns;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  @Test
  void returnsRowsOfThePageInLegacyOrderForTheActiveYearOnly() {
    List<QuestionnaireRow> rows = questionnaire.findRowsByPage("B1");
    assertThat(rows).extracting(QuestionnaireRow::entryId).containsExactly(101L, 102L, 103L, 104L);
    assertThat(rows.get(0).numeral()).isEqualTo("B2.");
  }

  @Test
  void replacesTheLegacyYearPlaceholder() {
    assertThat(questionnaire.findRowsByPage("B1").get(0).text()).endsWith("durante el 2026");
  }

  @Test
  void carriesLegacySkipAndAnswerObject() {
    QuestionnaireRow row = questionnaire.findRowsByPage("B1").get(1);
    assertThat(row.legacySkipCount()).isEqualTo(2);
    assertThat(row.legacySkipValue()).isEqualTo("3");
    assertThat(row.objectCode()).isEqualTo(1);
    assertThat(row.optionGroup()).isEqualTo(10);
  }

  @Test
  void returnsHeadersOfTheActiveYearSubtitlesFirst() {
    Map<Long, List<AdminHeader>> headers =
        questionnaire.findAdminHeaders(List.of(101L, 102L), "2026");
    assertThat(headers.get(101L))
        .extracting(AdminHeader::kind)
        .containsExactly(HeaderKind.SUBTITLE, HeaderKind.HEADING);
    assertThat(headers).doesNotContainKey(102L);
  }

  @Test
  void returnsQuestionAndHeaderHelpAsStoredWithTheirDefinition() {
    assertThat(questionnaire.findQuestionHelp(List.of(101L, 102L)))
        .containsOnlyKeys(101L)
        .extractingByKey(101L)
        .asInstanceOf(list(HelpRow.class))
        .containsExactly(
            new HelpRow(
                101,
                "desempeña",
                true,
                "Desempe&ntilde;o",
                "<p>Definici&oacute;n <b>ficticia</b> de desempe&ntilde;o.</p>"));
    assertThat(questionnaire.findHeaderHelp(List.of(1L, 2L)).get(1L))
        .extracting(HelpRow::term, HelpRow::hasDefinition)
        .containsExactly(tuple("laboral", true));
  }

  @Test
  void helpWithoutDefinitionIsReturnedFlaggedAndInOrder() {
    jdbc.update(
        "INSERT INTO FRM_PRE_AYUDA (COD_PRE_AYU, COD_CAP_PROY, COD_AYU, TEXTO)"
            + " VALUES (991, 102, 9999, 'Nivel'), (990, 102, 501, 'otra')",
        Map.of());
    try {
      assertThat(questionnaire.findQuestionHelp(List.of(102L)).get(102L))
          .extracting(HelpRow::term, HelpRow::hasDefinition, HelpRow::title)
          .containsExactly(tuple("otra", true, "Desempe&ntilde;o"), tuple("Nivel", false, null));
    } finally {
      jdbc.update("DELETE FROM FRM_PRE_AYUDA WHERE COD_PRE_AYU IN (990, 991)", Map.of());
    }
  }

  @Test
  void ordersOptionsNumericallyLikeTheLegacySystem() {
    Map<Integer, List<AnswerOption>> options = questionnaire.findOptions(List.of(10));
    assertThat(options.get(10)).extracting(AnswerOption::value).containsExactly("1", "2", "3");
  }

  // El mismo texto de FRM_RESPUESTA puede tener otro valor en cada grupo: el valor sale del grupo.
  @Test
  void optionValueComesFromTheAnswerGroup() {
    jdbc.update(
        "INSERT INTO FRM_RTA_GRUPO (COD_GRUPO, GRUPO, ORDEN, VALOR, COD_RTA)"
            + " VALUES (990, 990, '1', '7', 1)",
        Map.of());
    try {
      assertThat(questionnaire.findOptions(List.of(990)).get(990))
          .containsExactly(new AnswerOption("7", "Asistencial"));
    } finally {
      jdbc.update("DELETE FROM FRM_RTA_GRUPO WHERE COD_GRUPO = 990", Map.of());
    }
  }

  @Test
  void emptyInputsDoNotHitTheDatabase() {
    assertThat(questionnaire.findAdminHeaders(List.of(), "2026")).isEmpty();
    assertThat(questionnaire.findQuestionHelp(List.of())).isEmpty();
    assertThat(questionnaire.findHeaderHelp(List.of())).isEmpty();
    assertThat(questionnaire.findOptions(List.of())).isEmpty();
  }

  @Test
  void listsTheRealColumnsOfAnAnswerTable() {
    assertThat(answerColumns.findColumns("EDIN_20_CAP_B_UBICA"))
        .contains("B02", "B06")
        .doesNotContain("B99");
    assertThat(answerColumns.findColumns("NO_EXISTE")).isEmpty();
  }
}
