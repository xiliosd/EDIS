package co.gov.dane.edi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.gov.dane.edi.model.AnswerColumn;
import co.gov.dane.edi.model.PageState;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.repository.AnswerRepository;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleErrorCodes;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SurveyAnswerServiceTest {

  private static final AnswerColumn BYTES_2 =
      new AnswerColumn("D05", "VARCHAR2", 2, 2, false, null, null);
  private static final AnswerColumn CHARS_2 =
      new AnswerColumn("X01", "VARCHAR2", 8, 2, true, null, null);
  private static final AnswerColumn INTEGER_3 =
      new AnswerColumn("B01", "NUMBER", 22, 0, false, 3, 0);
  private static final AnswerColumn DECIMAL_5_2 =
      new AnswerColumn("X02", "NUMBER", 22, 0, false, 5, 2);
  private static final AnswerColumn FREE_NUMBER =
      new AnswerColumn("X03", "NUMBER", 22, 0, false, null, null);

  @Test
  void byteSemanticsCountsUtf8Bytes() {
    assertThat(SurveyAnswerService.checkColumn("12", BYTES_2)).isEmpty();
    assertThat(SurveyAnswerService.checkColumn("ñ", BYTES_2)).isEmpty();
    assertThat(SurveyAnswerService.checkColumn("ññ", BYTES_2)).contains(RuleErrorCodes.TOO_LONG);
    assertThat(SurveyAnswerService.checkColumn("123", BYTES_2)).contains(RuleErrorCodes.TOO_LONG);
  }

  @Test
  void charSemanticsCountsCharacters() {
    assertThat(SurveyAnswerService.checkColumn("ññ", CHARS_2)).isEmpty();
    assertThat(SurveyAnswerService.checkColumn("ñññ", CHARS_2)).contains(RuleErrorCodes.TOO_LONG);
  }

  @Test
  void integerColumnRejectsDecimalsAndTooManyDigits() {
    assertThat(SurveyAnswerService.checkColumn("999", INTEGER_3)).isEmpty();
    assertThat(SurveyAnswerService.checkColumn("-007", INTEGER_3)).isEmpty();
    assertThat(SurveyAnswerService.checkColumn("1.0", INTEGER_3)).isEmpty();
    assertThat(SurveyAnswerService.checkColumn("1.5", INTEGER_3))
        .contains(RuleErrorCodes.INVALID_TYPE);
    assertThat(SurveyAnswerService.checkColumn("1000", INTEGER_3))
        .contains(RuleErrorCodes.OUT_OF_RANGE);
    assertThat(SurveyAnswerService.checkColumn("abc", INTEGER_3))
        .contains(RuleErrorCodes.INVALID_TYPE);
  }

  @Test
  void decimalColumnRespectsPrecisionAndScale() {
    assertThat(SurveyAnswerService.checkColumn("999.99", DECIMAL_5_2)).isEmpty();
    assertThat(SurveyAnswerService.checkColumn("0.5", DECIMAL_5_2)).isEmpty();
    assertThat(SurveyAnswerService.checkColumn("1.234", DECIMAL_5_2))
        .contains(RuleErrorCodes.INVALID_TYPE);
    assertThat(SurveyAnswerService.checkColumn("1000", DECIMAL_5_2))
        .contains(RuleErrorCodes.OUT_OF_RANGE);
  }

  @Test
  void numberWithoutPrecisionOnlyNeedsToBeNumeric() {
    assertThat(SurveyAnswerService.checkColumn("123456789.123", FREE_NUMBER)).isEmpty();
    assertThat(SurveyAnswerService.checkColumn("1,5", FREE_NUMBER))
        .contains(RuleErrorCodes.INVALID_TYPE);
  }

  @Test
  void observationsThatUpdateNoRowAreLoggedWithoutTextOrCode(CapturedOutput output) {
    SurveyProgressService progress = mock(SurveyProgressService.class);
    when(progress.stateOf(anyString(), any(SurveyPage.class))).thenReturn(PageState.SAVED);
    AnswerRepository answers = mock(AnswerRepository.class);
    when(answers.findObservations("9000000998")).thenReturn(null);
    when(answers.saveObservations("9000000998", "Texto ficticio")).thenReturn(0);
    AnswerColumnRepository columns = mock(AnswerColumnRepository.class);
    when(columns.findColumnDetails(anyString())).thenReturn(Map.of());
    ReceiptService receipts = mock(ReceiptService.class);
    when(receipts.receiptFor("9000000998")).thenReturn("ABCDE-23456");
    SurveyAnswerService service =
        new SurveyAnswerService(
            progress,
            mock(QuestionnaireService.class),
            mock(EffectiveRulesService.class),
            answers,
            columns,
            new SaveLocks(),
            receipts);

    assertThat(service.finish("9000000998", "Texto ficticio").receipt()).isEqualTo("ABCDE-23456");
    assertThat(output)
        .contains("Las observaciones no se guardaron")
        .doesNotContain("Texto ficticio")
        .doesNotContain("9000000998");
  }

  @Test
  void echoedKeysAreCutAtThirtyCharacters() {
    assertThat(SurveyAnswerService.echoedKey("B06")).isEqualTo("B06");
    assertThat(SurveyAnswerService.echoedKey("X".repeat(40))).hasSize(30);
  }
}
