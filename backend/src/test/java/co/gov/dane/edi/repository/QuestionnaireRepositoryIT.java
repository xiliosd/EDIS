package co.gov.dane.edi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.model.AnswerOption;
import co.gov.dane.edi.model.HeaderKind;
import co.gov.dane.edi.model.SectionHeader;
import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuestionnaireRepositoryIT extends OracleIntegrationTest {

  @Autowired private QuestionnaireRepository questionnaire;
  @Autowired private AnswerColumnRepository answerColumns;

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
    Map<Long, List<SectionHeader>> headers = questionnaire.findHeaders(List.of(101L, 102L));
    assertThat(headers.get(101L))
        .extracting(SectionHeader::kind)
        .containsExactly(HeaderKind.SUBTITLE, HeaderKind.HEADING);
    assertThat(headers).doesNotContainKey(102L);
  }

  @Test
  void ordersOptionsNumericallyLikeTheLegacySystem() {
    Map<Integer, List<AnswerOption>> options = questionnaire.findOptions(List.of(10));
    assertThat(options.get(10)).extracting(AnswerOption::value).containsExactly("1", "2", "3");
  }

  @Test
  void emptyInputsDoNotHitTheDatabase() {
    assertThat(questionnaire.findHeaders(List.of())).isEmpty();
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
