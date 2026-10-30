package co.gov.dane.edi.model;

import co.gov.dane.edi.rules.FieldValidation;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.SkipRule;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Reglas que ve el encuestado: los mismos nombres JSON del archivo (spec §5.1), sin el estado del
 * año, las tablas de respuesta ni las columnas nuevas, que solo interesan a la administración.
 */
public record RespondentRules(
    @JsonProperty("encuesta") String survey,
    @JsonProperty("anio") int year,
    @JsonProperty("version") int version,
    @JsonProperty("saltos") List<SkipRule> skips,
    @JsonProperty("validaciones") Map<String, FieldValidation> validations) {

  public static RespondentRules from(RuleSet rules) {
    return new RespondentRules(
        rules.survey(), rules.year(), rules.version(), rules.skips(), rules.validations());
  }
}
