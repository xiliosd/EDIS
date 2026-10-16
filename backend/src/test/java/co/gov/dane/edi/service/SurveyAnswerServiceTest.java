package co.gov.dane.edi.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.model.AnswerColumn;
import co.gov.dane.edi.rules.RuleErrorCodes;
import org.junit.jupiter.api.Test;

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
}
