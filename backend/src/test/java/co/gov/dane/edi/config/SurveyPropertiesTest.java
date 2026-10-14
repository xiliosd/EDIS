package co.gov.dane.edi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SurveyPropertiesTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsBothLegacySurveyCodes() {
    assertThat(VALIDATOR.validate(new SurveyProperties("EDIN", 2026, "DANE_DIRPEN", "/data")))
        .isEmpty();
    assertThat(VALIDATOR.validate(new SurveyProperties("EDID", 2026, "DANE_DIRPEN", "/data")))
        .isEmpty();
  }

  @Test
  void rejectsUnknownSurveyCode() {
    Set<ConstraintViolation<SurveyProperties>> violations =
        VALIDATOR.validate(new SurveyProperties("XYZ", 2026, "DANE_DIRPEN", "/data"));
    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("code");
  }

  @Test
  void rejectsSchemaNameThatCouldAlterSql() {
    Set<ConstraintViolation<SurveyProperties>> violations =
        VALIDATOR.validate(new SurveyProperties("EDIN", 2026, "DANE; DROP", "/data"));
    assertThat(violations)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("dbSchema");
  }

  @Test
  void mapsSurveyToItsFixedSampleTable() {
    assertThat(new SurveyProperties("EDIN", 2026, "S", "/d").sampleTableName())
        .isEqualTo("EDIN_MUESTRA");
    assertThat(new SurveyProperties("EDID", 2026, "S", "/d").sampleTableName())
        .isEqualTo("EDID_MUESTRA");
    assertThatThrownBy(() -> new SurveyProperties("X", 2026, "S", "/d").sampleTableName())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void exposesYearAsTextBecauseLegacyColumnsAreVarchar() {
    assertThat(new SurveyProperties("EDIN", 2026, "S", "/d").yearText()).isEqualTo("2026");
  }
}
