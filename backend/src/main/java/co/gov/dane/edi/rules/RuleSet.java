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
    @JsonProperty("tablas") @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> tables,
    @JsonProperty("columnasNuevas") @JsonInclude(JsonInclude.Include.NON_EMPTY)
        Map<String, NewColumn> newColumns,
    @JsonProperty("casos") @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TestCase> cases,
    @JsonProperty("publicacion") @JsonInclude(JsonInclude.Include.NON_NULL) Publication publication,
    @JsonProperty("ultimoCambio") @JsonInclude(JsonInclude.Include.NON_NULL)
        ChangeNote lastChange) {

  public RuleSet {
    skips = skips == null ? List.of() : List.copyOf(skips);
    validations = validations == null ? Map.of() : Map.copyOf(validations);
    tables = tables == null ? Map.of() : Map.copyOf(tables);
    newColumns = newColumns == null ? Map.of() : Map.copyOf(newColumns);
    cases = cases == null ? List.of() : List.copyOf(cases);
  }

  public static RuleSet empty(String survey, int year) {
    return new RuleSet(
        survey, year, 0, List.of(), Map.of(), null, Map.of(), Map.of(), List.of(), null, null);
  }

  /** Un archivo sin {@code estado} se considera publicado y de solo lectura. */
  public YearState effectiveState() {
    return state == null ? YearState.PUBLICADO : state;
  }

  public RuleSet withState(YearState newState) {
    return new RuleSet(
        survey,
        year,
        version,
        skips,
        validations,
        newState,
        tables,
        newColumns,
        cases,
        publication,
        lastChange);
  }

  public RuleSet withTables(Map<String, String> newTables) {
    return new RuleSet(
        survey,
        year,
        version,
        skips,
        validations,
        state,
        newTables,
        newColumns,
        cases,
        publication,
        lastChange);
  }

  public RuleSet withVersion(int newVersion) {
    return new RuleSet(
        survey,
        year,
        newVersion,
        skips,
        validations,
        state,
        tables,
        newColumns,
        cases,
        publication,
        lastChange);
  }

  public RuleSet withNewColumns(Map<String, NewColumn> columns) {
    return new RuleSet(
        survey,
        year,
        version,
        skips,
        validations,
        state,
        tables,
        columns,
        cases,
        publication,
        lastChange);
  }

  public RuleSet withSkips(List<SkipRule> newSkips) {
    return new RuleSet(
        survey,
        year,
        version,
        newSkips,
        validations,
        state,
        tables,
        newColumns,
        cases,
        publication,
        lastChange);
  }

  public RuleSet withValidations(Map<String, FieldValidation> newValidations) {
    return new RuleSet(
        survey,
        year,
        version,
        skips,
        newValidations,
        state,
        tables,
        newColumns,
        cases,
        publication,
        lastChange);
  }

  public RuleSet withCases(List<TestCase> newCases) {
    return new RuleSet(
        survey,
        year,
        version,
        skips,
        validations,
        state,
        tables,
        newColumns,
        newCases,
        publication,
        lastChange);
  }

  public RuleSet withPublication(Publication newPublication) {
    return new RuleSet(
        survey,
        year,
        version,
        skips,
        validations,
        state,
        tables,
        newColumns,
        cases,
        newPublication,
        lastChange);
  }

  public RuleSet withLastChange(ChangeNote newLastChange) {
    return new RuleSet(
        survey,
        year,
        version,
        skips,
        validations,
        state,
        tables,
        newColumns,
        cases,
        publication,
        newLastChange);
  }
}
