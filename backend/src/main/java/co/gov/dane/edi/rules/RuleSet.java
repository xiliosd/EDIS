package co.gov.dane.edi.rules;

import co.gov.dane.edi.admin.YearState;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Reglas de saltos y validaciones de una encuesta y año (spec §5.1), con el estado del año y la
 * tabla de respuestas de cada capítulo. {@code estado} nulo equivale a publicado y {@code tablas}
 * vacío a las tablas por defecto de {@code Chapter}; ambos se omiten del JSON cuando no aplican.
 */
public record RuleSet(
    @JsonProperty("encuesta") String survey,
    @JsonProperty("anio") int year,
    @JsonProperty("version") int version,
    @JsonProperty("saltos") List<SkipRule> skips,
    @JsonProperty("validaciones") Map<String, FieldValidation> validations,
    @JsonProperty("estado") @JsonInclude(JsonInclude.Include.NON_NULL) YearState state,
    @JsonProperty("tablas") @JsonInclude(JsonInclude.Include.NON_EMPTY)
        Map<String, String> tables) {

  public RuleSet {
    skips = skips == null ? List.of() : List.copyOf(skips);
    validations = validations == null ? Map.of() : Map.copyOf(validations);
    tables = tables == null ? Map.of() : Map.copyOf(tables);
  }

  public static RuleSet empty(String survey, int year) {
    return new RuleSet(survey, year, 0, List.of(), Map.of(), null, Map.of());
  }

  /** Un archivo sin {@code estado} se considera publicado y de solo lectura. */
  public YearState effectiveState() {
    return state == null ? YearState.PUBLICADO : state;
  }

  public RuleSet withState(YearState newState) {
    return new RuleSet(survey, year, version, skips, validations, newState, tables);
  }

  public RuleSet withTables(Map<String, String> newTables) {
    return new RuleSet(survey, year, version, skips, validations, state, newTables);
  }

  public RuleSet withVersion(int newVersion) {
    return new RuleSet(survey, year, newVersion, skips, validations, state, tables);
  }
}
