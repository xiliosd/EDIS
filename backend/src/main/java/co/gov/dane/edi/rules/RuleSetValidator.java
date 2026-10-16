package co.gov.dane.edi.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Valida la estructura de las reglas antes de usarlas; un error detiene la carga con un mensaje.
 */
public final class RuleSetValidator {

  static final Set<String> OPERATORS =
      Set.of("eq", "ne", "in", "notIn", "gt", "lt", "empty", "notEmpty");
  static final Set<String> TYPES = Set.of("entero", "decimal", "texto", "opcion");
  private static final String DISABLE = "deshabilitar";
  private static final Pattern COLUMN = Pattern.compile("[A-Z][0-9A-Z_]{1,29}");

  private RuleSetValidator() {}

  public static void validate(RuleSet rules) {
    List<String> problems = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (SkipRule rule : rules.skips()) {
      String id = rule.id() == null ? "(sin id)" : rule.id();
      if (rule.id() == null || !ids.add(rule.id())) {
        problems.add("Regla con id ausente o repetido: " + id);
      }
      if (rule.then() == null || !DISABLE.equals(rule.then().action())) {
        problems.add(
            "Regla "
                + id
                + ": acción no soportada "
                + (rule.then() == null ? "(ninguna)" : rule.then().action()));
      } else if (rule.then().questions() == null || rule.then().questions().isEmpty()) {
        problems.add("Regla " + id + ": no indica preguntas a deshabilitar");
      } else {
        rule.then().questions().forEach(q -> checkColumn(id, q, problems));
      }
      if (rule.when() == null) {
        problems.add("Regla " + id + ": no tiene condición");
      } else {
        Set<String> triggers = new HashSet<>();
        checkCondition(id, rule.when(), triggers, problems);
        if (rule.then() != null && rule.then().questions() != null) {
          rule.then().questions().stream()
              .filter(triggers::contains)
              .forEach(q -> problems.add("Regla " + id + ": deshabilita su propia pregunta " + q));
        }
      }
    }
    for (Map.Entry<String, FieldValidation> entry : rules.validations().entrySet()) {
      checkColumn("validación", entry.getKey(), problems);
      FieldValidation validation = entry.getValue();
      if (validation.type() != null && !TYPES.contains(validation.type())) {
        problems.add("Validación " + entry.getKey() + ": tipo desconocido " + validation.type());
      }
      if (validation.min() != null
          && validation.max() != null
          && validation.min().compareTo(validation.max()) > 0) {
        problems.add("Validación " + entry.getKey() + ": min mayor que max");
      }
    }
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Reglas inválidas: " + String.join("; ", problems));
    }
  }

  private static void checkCondition(
      String id, Condition condition, Set<String> triggers, List<String> problems) {
    if (condition.and() != null || condition.or() != null) {
      List<Condition> nested = condition.and() != null ? condition.and() : condition.or();
      if (nested.isEmpty()) {
        problems.add("Regla " + id + ": y/o sin condiciones");
      }
      nested.forEach(child -> checkCondition(id, child, triggers, problems));
      return;
    }
    if (!OPERATORS.contains(condition.op())) {
      problems.add("Regla " + id + ": operador desconocido " + condition.op());
    }
    List<String> targets =
        condition.question() != null
            ? List.of(condition.question())
            : condition.all() != null ? condition.all() : condition.any();
    if (targets == null || targets.isEmpty()) {
      problems.add("Regla " + id + ": la condición no indica preguntas");
      return;
    }
    targets.forEach(q -> checkColumn(id, q, problems));
    triggers.addAll(targets);
  }

  private static void checkColumn(String id, String column, List<String> problems) {
    if (column == null || !COLUMN.matcher(column).matches()) {
      problems.add("Regla " + id + ": columna inválida " + column);
    }
  }
}
