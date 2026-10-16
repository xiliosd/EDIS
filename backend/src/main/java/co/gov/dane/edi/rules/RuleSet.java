package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Reglas de saltos y validaciones de una encuesta y año (spec §5.1). */
public record RuleSet(
    @JsonProperty("encuesta") String survey,
    @JsonProperty("anio") int year,
    @JsonProperty("version") int version,
    @JsonProperty("saltos") List<SkipRule> skips,
    @JsonProperty("validaciones") Map<String, FieldValidation> validations) {

  public RuleSet {
    skips = skips == null ? List.of() : List.copyOf(skips);
    validations = validations == null ? Map.of() : Map.copyOf(validations);
  }

  public static RuleSet empty(String survey, int year) {
    return new RuleSet(survey, year, 0, List.of(), Map.of());
  }
}
