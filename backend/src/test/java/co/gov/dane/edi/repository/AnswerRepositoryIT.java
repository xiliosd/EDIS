package co.gov.dane.edi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AnswerRepositoryIT extends OracleIntegrationTest {

  private static final String CODE = "9000000701";

  @Autowired private AnswerRepository answers;
  @Autowired private AnswerColumnRepository columns;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    for (Chapter chapter : Chapter.values()) {
      jdbc.update("DELETE FROM " + chapter.tableName() + " WHERE C_C = :cc", Map.of("cc", CODE));
    }
  }

  @Test
  void insertsFirstPageWithFixedColumnsAndYearWhenTheTableHasIt() {
    answers.insertPage(Chapter.B, CODE, Map.of("B02", "1", "B06", "O'Br"));

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT ESTADO, TC, ANIO, B02, B06 FROM EDIN_20_CAP_B_UBICA WHERE C_C = :cc",
            Map.of("cc", CODE));
    assertThat(row)
        .containsEntry("ANIO", "2026")
        .containsEntry("B02", "1")
        .containsEntry("B06", "O'Br");
    assertThat(((Number) row.get("ESTADO")).intValue()).isEqualTo(1);
    assertThat(((Number) row.get("TC")).intValue()).isEqualTo(1);
    assertThat(answers.findAnswers(Chapter.B, CODE, List.of("B02", "B06", "B07")))
        .isEqualTo(Map.of("B02", "1", "B06", "O'Br"));
  }

  @Test
  void tablesWithoutYearColumnStillGetARow() {
    answers.insertPage(Chapter.C, CODE, Map.of("C01A", "2", "C01B", "0"));
    assertThat(answers.findAnswers(Chapter.C, CODE, List.of("C01A", "C01B", "C13")))
        .isEqualTo(Map.of("C01A", "2", "C01B", "0"));
  }

  @Test
  void secondPageIsWrittenOnlyOnce() {
    answers.insertPage(Chapter.C, CODE, Map.of("C01A", "1", "C01B", "1"));
    assertThat(answers.updatePage(Chapter.C, CODE, Map.of("C13", "1"), "C13")).isEqualTo(1);
    assertThat(answers.updatePage(Chapter.C, CODE, Map.of("C13", "2"), "C13")).isZero();
    assertThat(answers.findAnswers(Chapter.C, CODE, List.of("C13"))).containsEntry("C13", "1");
  }

  @Test
  void observationsAreWrittenOnlyOnce() {
    answers.insertPage(Chapter.H, CODE, Map.of("H01", "1"));
    assertThat(answers.findObservations(CODE)).isNull();
    assertThat(answers.saveObservations(CODE, "Primera")).isEqualTo(1);
    assertThat(answers.saveObservations(CODE, "Segunda")).isZero();
    assertThat(answers.findObservations(CODE)).isEqualTo("Primera");
  }

  @Test
  void unknownOrUnsafeColumnsAreRejectedBeforeTouchingTheDatabase() {
    assertThatThrownBy(() -> answers.insertPage(Chapter.B, CODE, Map.of("B99", "1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> answers.findAnswers(Chapter.B, CODE, List.of("B02; DROP")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateRequiresTheGuardColumnWithANonBlankValue() {
    answers.insertPage(Chapter.C, CODE, Map.of("C01A", "1", "C01B", "1"));
    assertThatThrownBy(() -> answers.updatePage(Chapter.C, CODE, Map.of("C13", " "), "C13"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> answers.updatePage(Chapter.C, CODE, Map.of("C01B", "2"), "C13"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> answers.updatePage(Chapter.C, CODE, Map.of(), "C13"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void systemColumnsCannotBeWrittenAsAnswers() {
    answers.insertPage(Chapter.H, CODE, Map.of("H01", "1"));
    for (String column : List.of("C_C", "ESTADO", "TC", "COMPLETA", "OBS")) {
      assertThatThrownBy(
              () -> answers.updatePage(Chapter.H, CODE, Map.of("H01", "1", column, "x"), "H01"))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> answers.insertPage(Chapter.B, CODE, Map.of("ANIO", "1999")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exposesColumnTypeAndLength() {
    assertThat(columns.findColumnDetails("EDIN_20_CAP_B_UBICA").get("B02").dataLength())
        .isEqualTo(1);
  }
}
