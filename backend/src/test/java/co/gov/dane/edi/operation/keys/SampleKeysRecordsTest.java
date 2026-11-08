package co.gov.dane.edi.operation.keys;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.operation.keys.SampleKeysRepository.Assignment;
import co.gov.dane.edi.operation.keys.SampleKeysRepository.PendingRow;
import org.junit.jupiter.api.Test;

/** Los registros de la generación de claves no exponen cédula ni clave al imprimirse en un log. */
class SampleKeysRecordsTest {

  private static final String RESPONDENT_CODE = "900000099";

  @Test
  void pendingRowHidesTheRespondentCode() {
    PendingRow row = new PendingRow("AAAFicticio", RESPONDENT_CODE);

    assertThat(row.toString()).doesNotContain(RESPONDENT_CODE).contains("AAAFicticio");
  }

  @Test
  void assignmentHidesEverything() {
    Assignment assignment =
        new Assignment(new PendingRow("AAAFicticio", RESPONDENT_CODE), "ABCDEFGH", "HGFEDCBA");

    assertThat(assignment.toString())
        .doesNotContain(RESPONDENT_CODE)
        .doesNotContain("ABCDEFGH")
        .doesNotContain("HGFEDCBA");
  }
}
