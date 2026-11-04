package co.gov.dane.edi.rules;

import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.SurveyPage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private static final Pattern TABLE = Pattern.compile("[A-Z][A-Z0-9_]{1,29}");

  private static final Pattern CASE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
  private static final int MAX_CASE_NAME = 100;

  /**
   * Tope de saltos por archivo. El cuestionario real tiene unas 160 reglas; el tope deja holgura y
   * acota el análisis de lógica, que crece con el cuadrado de las reglas que comparten destino.
   */
  public static final int MAX_SKIPS = 1000;

  /** Tope de casos de prueba por archivo: cada guardado los vuelve a correr todos. */
  public static final int MAX_CASES = 1000;

  private RuleSetValidator() {}

  public static void validate(RuleSet rules) {
    List<String> problems = problems(rules);
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Reglas inválidas: " + String.join("; ", problems));
    }
  }

  /** Los problemas estructurales de las reglas, sin lanzar; vacío si son válidas. */
  public static List<String> problems(RuleSet rules) {
    List<String> problems = new ArrayList<>();
    // Un archivo fuera de tamaño se rechaza entero, sin revisar ni listar cada regla o caso.
    if (rules.skips().size() > MAX_SKIPS) {
      problems.add("El archivo tiene más de " + MAX_SKIPS + " reglas.");
    }
    if (rules.cases().size() > MAX_CASES) {
      problems.add("El archivo tiene más de " + MAX_CASES + " casos de prueba.");
    }
    if (!problems.isEmpty()) {
      return problems;
    }
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
    for (Map.Entry<String, String> entry : rules.tables().entrySet()) {
      if (Chapter.fromCode(entry.getKey()).isEmpty()
          || !Chapter.fromCode(entry.getKey()).get().code().equals(entry.getKey())) {
        problems.add("Tablas: capítulo desconocido " + entry.getKey());
      }
      if (entry.getValue() == null || !TABLE.matcher(entry.getValue()).matches()) {
        problems.add("Tablas: nombre de tabla inválido para el capítulo " + entry.getKey());
      }
    }
    for (Map.Entry<String, NewColumn> entry : rules.newColumns().entrySet()) {
      NewColumn column = entry.getValue();
      if (!COLUMN.matcher(entry.getKey()).matches()) {
        problems.add("Columnas nuevas: nombre de columna inválido " + entry.getKey());
      }
      String problem = column == null ? "falta la definición" : definitionProblem(column);
      if (problem != null) {
        problems.add("Columnas nuevas: " + entry.getKey() + ": " + problem);
      }
    }
    checkCases(rules.cases(), problems);
    return problems;
  }

  private static void checkCases(List<TestCase> cases, List<String> problems) {
    Set<String> caseIds = new HashSet<>();
    for (TestCase testCase : cases) {
      String id = testCase.id() == null ? "(sin id)" : testCase.id();
      if (testCase.id() == null || !CASE_ID.matcher(testCase.id()).matches()) {
        problems.add("Caso con id inválido: " + id);
      } else if (!caseIds.add(testCase.id())) {
        problems.add("Caso con id repetido: " + id);
      }
      if (testCase.name() == null
          || testCase.name().isBlank()
          || testCase.name().length() > MAX_CASE_NAME) {
        problems.add("Caso " + id + ": el nombre debe tener entre 1 y 100 caracteres");
      }
      if (SurveyPage.find(testCase.pageCode()).isEmpty()) {
        problems.add("Caso " + id + ": página desconocida " + testCase.pageCode());
      }
      checkCaseNulls(id, testCase, problems);
    }
  }

  /** Un null del JSON no es una respuesta ni una pregunta: se rechaza en vez de adivinarlo. */
  private static void checkCaseNulls(String id, TestCase testCase, List<String> problems) {
    testCase.answers().entrySet().stream()
        .filter(entry -> entry.getValue() == null)
        .forEach(
            entry ->
                problems.add("Caso " + id + ": la respuesta de " + entry.getKey() + " es null"));
    if (testCase.expected().hidden().stream().anyMatch(Objects::isNull)) {
      problems.add("Caso " + id + ": las preguntas ocultas esperadas tienen un null");
    }
    if (testCase.expected().errors() != null) {
      testCase.expected().errors().entrySet().stream()
          .filter(entry -> entry.getValue() == null)
          .forEach(
              entry ->
                  problems.add(
                      "Caso " + id + ": el error esperado de " + entry.getKey() + " es null"));
    }
  }

  private static String definitionProblem(NewColumn column) {
    String type = column.type();
    Integer length = column.length();
    if (!"VARCHAR2".equals(type) && !"NUMBER".equals(type)) {
      return "el tipo debe ser VARCHAR2 o NUMBER";
    }
    int maximum = "VARCHAR2".equals(type) ? 4000 : 38;
    if (length == null || length < 1 || length > maximum) {
      return "longitud fuera de rango (1 a " + maximum + ")";
    }
    return null;
  }

  private static void checkCondition(
      String id, Condition condition, Set<String> triggers, List<String> problems) {
    if (condition == null) {
      problems.add("Regla " + id + ": hay una condición vacía (null)");
      return;
    }
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
    if (condition.values() != null && condition.values().stream().anyMatch(Objects::isNull)) {
      problems.add("Regla " + id + ": la lista de valores tiene un valor vacío (null)");
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
