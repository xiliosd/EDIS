package co.gov.dane.edi.rules;

import co.gov.dane.edi.model.AnswerColumn;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.service.SurveyAnswerService;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Analiza las reglas efectivas de un año contra sus preguntas (spec §5) y devuelve errores, que
 * bloquean el guardado. Es puro y determinista: los hallazgos salen ordenados por elemento y
 * código.
 */
public final class RuleSetAnalyzer {

  /** Tope de combinaciones que se enumeran por pregunta o regla; más allá, NO_ANALIZABLE. */
  public static final int MAX_COMBINATIONS = 4096;

  /** Evaluaciones totales por llamada a analyze; al agotarse, el resto es NO_ANALIZABLE. */
  public static final int MAX_TOTAL_EVALUATIONS = 200_000;

  private static final Set<String> VALUE_OPERATORS = Set.of("eq", "ne", "in", "notIn");

  private RuleSetAnalyzer() {}

  public static AnalysisReport analyze(RuleSet effective, Map<String, QuestionFacts> facts) {
    List<Finding> errors = new ArrayList<>();
    RuleSetValidator.problems(effective)
        .forEach(
            problem ->
                errors.add(new Finding(AnalysisCodes.ESTRUCTURA, "archivo", problem, List.of())));
    checkUnknownQuestions(effective, facts, errors);
    checkCycles(effective, errors);
    checkOptionValues(effective, facts, errors);
    effective
        .validations()
        .forEach(
            (column, validation) -> {
              checkRange(column, validation, errors);
              checkColumnCapacity(effective, column, validation, facts.get(column), errors);
              checkValidationOnOption(column, validation, facts.get(column), errors);
            });
    errors.sort(
        Comparator.comparing(Finding::element)
            .thenComparing(Finding::code)
            .thenComparing(Finding::message));
    return new AnalysisReport(errors, warnings(effective, facts, errors));
  }

  /**
   * Los avisos salen ordenados por elemento y código. La enumeración se omite si hay problemas
   * estructurales y salta las reglas que ya tienen CICLO, para no duplicar errores.
   */
  private static List<Finding> warnings(
      RuleSet effective, Map<String, QuestionFacts> facts, List<Finding> errors) {
    List<Finding> warnings = new ArrayList<>(LogicWarnings.structural(effective, facts));
    boolean structural = errors.stream().anyMatch(f -> f.code().equals(AnalysisCodes.ESTRUCTURA));
    if (!structural) {
      Set<String> cyclicRuleIds = new HashSet<>();
      errors.stream()
          .filter(f -> f.code().equals(AnalysisCodes.CICLO))
          .forEach(f -> cyclicRuleIds.add(f.element().substring("regla:".length())));
      warnings.addAll(new LogicWarnings(effective, facts, cyclicRuleIds).enumerated());
    }
    warnings.sort(
        Comparator.comparing(Finding::element)
            .thenComparing(Finding::code)
            .thenComparing(f -> String.join(",", f.questions())));
    return warnings;
  }

  // ---- PREGUNTA_INEXISTENTE ----

  private static void checkUnknownQuestions(
      RuleSet rules, Map<String, QuestionFacts> facts, List<Finding> errors) {
    for (SkipRule rule : rules.skips()) {
      Set<String> columns = new TreeSet<>(triggersOf(rule));
      columns.addAll(targetsOf(rule));
      addUnknown("regla:" + rule.id(), columns, facts, errors);
    }
    rules
        .validations()
        .keySet()
        .forEach(column -> addUnknown("validacion:" + column, Set.of(column), facts, errors));
    for (TestCase testCase : rules.cases()) {
      Set<String> columns = new TreeSet<>(testCase.answers().keySet());
      columns.addAll(testCase.expected().hidden());
      if (testCase.expected().errors() != null) {
        columns.addAll(testCase.expected().errors().keySet());
      }
      addUnknown("caso:" + testCase.id(), columns, facts, errors);
    }
  }

  /** Un hallazgo por (elemento, columna): corregir una sola no cambia la identidad de las otras. */
  private static void addUnknown(
      String element, Set<String> columns, Map<String, QuestionFacts> facts, List<Finding> errors) {
    columns.stream()
        .filter(column -> !facts.containsKey(column))
        .sorted()
        .forEach(
            column ->
                errors.add(
                    new Finding(
                        AnalysisCodes.PREGUNTA_INEXISTENTE,
                        element,
                        "Pregunta inexistente en el cuestionario: " + column + ".",
                        List.of(column))));
  }

  // ---- CICLO ----

  /**
   * Una regla es parte de un ciclo si alguna de sus aristas (disparador a destino) queda dentro de
   * un componente fuertemente conexo no trivial, autociclos incluidos. {@code questions} son las
   * columnas del componente; así, borrar una regla de un componente con varios ciclos no crea
   * errores nuevos.
   */
  private static void checkCycles(RuleSet rules, List<Finding> errors) {
    Map<String, Set<String>> graph = new TreeMap<>();
    for (SkipRule rule : rules.skips()) {
      for (String trigger : triggersOf(rule)) {
        graph.computeIfAbsent(trigger, key -> new TreeSet<>()).addAll(targetsOf(rule));
      }
    }
    Map<String, List<String>> componentOf = new HashMap<>();
    for (List<String> component : new StrongComponents(graph).find()) {
      boolean selfLoop =
          component.size() == 1
              && graph.getOrDefault(component.get(0), Set.of()).contains(component.get(0));
      if (component.size() > 1 || selfLoop) {
        component.forEach(column -> componentOf.put(column, component));
      }
    }
    for (SkipRule rule : rules.skips()) {
      List<String> component = cycleComponentOf(rule, componentOf);
      if (component != null) {
        errors.add(
            new Finding(
                AnalysisCodes.CICLO,
                "regla:" + rule.id(),
                "La regla forma un ciclo de saltos entre: " + String.join(", ", component) + ".",
                component));
      }
    }
  }

  private static List<String> cycleComponentOf(
      SkipRule rule, Map<String, List<String>> componentOf) {
    Set<String> targets = targetsOf(rule);
    for (String trigger : triggersOf(rule)) {
      List<String> component = componentOf.get(trigger);
      if (component != null && targets.stream().anyMatch(component::contains)) {
        return component;
      }
    }
    return null;
  }

  /** Tarjan sobre un grafo ordenado: componentes y miembros salen en orden determinista. */
  private static final class StrongComponents {
    private final Map<String, Set<String>> graph;
    private final Map<String, Integer> index = new HashMap<>();
    private final Map<String, Integer> low = new HashMap<>();
    private final Deque<String> stack = new ArrayDeque<>();
    private final Set<String> onStack = new HashSet<>();
    private final List<List<String>> components = new ArrayList<>();

    StrongComponents(Map<String, Set<String>> graph) {
      this.graph = graph;
    }

    List<List<String>> find() {
      Set<String> nodes = new TreeSet<>(graph.keySet());
      graph.values().forEach(nodes::addAll);
      nodes.stream().filter(node -> !index.containsKey(node)).forEach(this::visit);
      return components;
    }

    private void visit(String node) {
      index.put(node, index.size());
      low.put(node, index.get(node));
      stack.push(node);
      onStack.add(node);
      for (String next : graph.getOrDefault(node, Set.of())) {
        if (!index.containsKey(next)) {
          visit(next);
          low.put(node, Math.min(low.get(node), low.get(next)));
        } else if (onStack.contains(next)) {
          low.put(node, Math.min(low.get(node), index.get(next)));
        }
      }
      if (low.get(node).equals(index.get(node))) {
        List<String> component = new ArrayList<>();
        String member;
        do {
          member = stack.pop();
          onStack.remove(member);
          component.add(member);
        } while (!member.equals(node));
        Collections.sort(component);
        components.add(component);
      }
    }
  }

  // ---- VALOR_NO_ES_OPCION ----

  private static void checkOptionValues(
      RuleSet rules, Map<String, QuestionFacts> facts, List<Finding> errors) {
    for (SkipRule rule : rules.skips()) {
      Map<String, Set<String>> badValues = new TreeMap<>();
      if (rule.when() != null) {
        collectBadValues(rule.when(), facts, badValues);
      }
      // Un hallazgo por (regla, columna), por la misma razón que PREGUNTA_INEXISTENTE.
      badValues.forEach(
          (column, values) ->
              errors.add(
                  new Finding(
                      AnalysisCodes.VALOR_NO_ES_OPCION,
                      "regla:" + rule.id(),
                      "El valor no es una opción de la pregunta "
                          + column
                          + ": "
                          + String.join(", ", values)
                          + ".",
                      List.of(column))));
    }
  }

  private static void collectBadValues(
      Condition condition, Map<String, QuestionFacts> facts, Map<String, Set<String>> bad) {
    forEachNested(condition, child -> collectBadValues(child, facts, bad));
    // Los nodos y/o no tienen operador: solo las hojas se revisan.
    if (condition.op() == null || !VALUE_OPERATORS.contains(condition.op())) {
      return;
    }
    List<String> values = new ArrayList<>();
    if (condition.value() != null) {
      values.add(condition.value());
    }
    if (condition.values() != null) {
      values.addAll(condition.values());
    }
    for (String column : leafColumns(condition)) {
      QuestionFacts question = facts.get(column);
      if (question == null || !isOption(question.inputType())) {
        continue;
      }
      values.stream()
          .filter(value -> !question.optionValues().contains(value))
          .forEach(value -> bad.computeIfAbsent(column, key -> new TreeSet<>()).add(value));
    }
  }

  // ---- RANGO_INVALIDO ----

  private static void checkRange(String column, FieldValidation validation, List<Finding> errors) {
    List<String> reasons = new ArrayList<>();
    BigDecimal min = validation.min();
    BigDecimal max = validation.max();
    if (min != null && max != null && min.compareTo(max) > 0) {
      reasons.add("min es mayor que max");
    }
    if (validation.maxLength() != null && validation.maxLength() < 1) {
      reasons.add("longitudMax es menor que 1");
    }
    if ("entero".equals(validation.type()) && (isFractional(min) || isFractional(max))) {
      reasons.add("el tipo entero lleva un límite con decimales");
    }
    if (!reasons.isEmpty()) {
      errors.add(
          new Finding(
              AnalysisCodes.RANGO_INVALIDO,
              "validacion:" + column,
              "Rango inválido en " + column + ": " + String.join(", ", reasons) + ".",
              List.of(column)));
    }
  }

  private static boolean isFractional(BigDecimal value) {
    return value != null && value.stripTrailingZeros().scale() > 0;
  }

  // ---- RANGO_EXCEDE_COLUMNA ----

  private static void checkColumnCapacity(
      RuleSet rules,
      String column,
      FieldValidation validation,
      QuestionFacts question,
      List<Finding> errors) {
    AnswerColumn capacity = question == null ? null : question.capacity();
    if (capacity == null) {
      capacity = capacityOf(column, rules.newColumns().get(column));
    }
    if (capacity == null) {
      return;
    }
    List<String> reasons = new ArrayList<>();
    if ("NUMBER".equals(capacity.dataType())) {
      addIfExceeds("min", validation.min(), capacity, reasons);
      addIfExceeds("max", validation.max(), capacity, reasons);
    } else if (validation.maxLength() != null) {
      int allowed = capacity.charSemantics() ? capacity.charLength() : capacity.dataLength();
      if (validation.maxLength() > allowed) {
        reasons.add("longitudMax " + validation.maxLength() + " supera " + allowed);
      }
    }
    if (!reasons.isEmpty()) {
      errors.add(
          new Finding(
              AnalysisCodes.RANGO_EXCEDE_COLUMNA,
              "validacion:" + column,
              "El rango de "
                  + column
                  + " no cabe en la columna: "
                  + String.join(", ", reasons)
                  + ".",
              List.of(column)));
    }
  }

  private static void addIfExceeds(
      String bound, BigDecimal value, AnswerColumn capacity, List<String> reasons) {
    if (value != null
        && SurveyAnswerService.checkColumn(value.toPlainString(), capacity).isPresent()) {
      reasons.add(bound + " " + value.toPlainString() + " no cabe");
    }
  }

  /** La capacidad que tendrá una columna aún no creada; el VARCHAR2 se mide en bytes. */
  private static AnswerColumn capacityOf(String column, NewColumn definition) {
    if (definition == null || definition.type() == null || definition.length() == null) {
      return null;
    }
    int length = definition.length();
    return switch (definition.type()) {
      case "NUMBER" -> new AnswerColumn(column, "NUMBER", 22, 0, false, length, 0);
      case "VARCHAR2" -> new AnswerColumn(column, "VARCHAR2", length, length, false, null, null);
      default -> null;
    };
  }

  // ---- VALIDACION_EN_OPCION ----

  private static void checkValidationOnOption(
      String column, FieldValidation validation, QuestionFacts question, List<Finding> errors) {
    if (question == null || !isOption(question.inputType())) {
      return;
    }
    boolean hasRange =
        validation.min() != null
            || validation.max() != null
            || validation.maxLength() != null
            || (validation.type() != null && !"opcion".equals(validation.type()));
    if (hasRange) {
      errors.add(
          new Finding(
              AnalysisCodes.VALIDACION_EN_OPCION,
              "validacion:" + column,
              "La pregunta " + column + " es de opciones: solo admite obligatoria.",
              List.of(column)));
    }
  }

  private static boolean isOption(InputType type) {
    return type == InputType.SINGLE_CHOICE || type == InputType.CHECKBOX;
  }

  // ---- Condiciones ----

  static Set<String> triggersOf(SkipRule rule) {
    Set<String> columns = new LinkedHashSet<>();
    if (rule.when() != null) {
      collectLeafColumns(rule.when(), columns);
    }
    return columns;
  }

  static Set<String> targetsOf(SkipRule rule) {
    if (rule.then() == null || rule.then().questions() == null) {
      return Set.of();
    }
    Set<String> targets = new LinkedHashSet<>(rule.then().questions());
    targets.remove(null);
    return targets;
  }

  private static void collectLeafColumns(Condition condition, Set<String> columns) {
    columns.addAll(leafColumns(condition));
    forEachNested(condition, child -> collectLeafColumns(child, columns));
  }

  private static List<String> leafColumns(Condition condition) {
    List<String> columns = new ArrayList<>();
    if (condition.question() != null) {
      columns.add(condition.question());
    }
    if (condition.all() != null) {
      columns.addAll(condition.all());
    }
    if (condition.any() != null) {
      columns.addAll(condition.any());
    }
    columns.removeIf(Objects::isNull);
    return columns;
  }

  private static void forEachNested(Condition condition, Consumer<Condition> action) {
    if (condition.and() != null) {
      condition.and().forEach(action);
    }
    if (condition.or() != null) {
      condition.or().forEach(action);
    }
  }
}
