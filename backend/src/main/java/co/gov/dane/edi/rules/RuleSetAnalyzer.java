package co.gov.dane.edi.rules;

import co.gov.dane.edi.model.AnswerColumn;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.service.SurveyAnswerService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
    return new AnalysisReport(errors, List.of());
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

  private static void addUnknown(
      String element, Set<String> columns, Map<String, QuestionFacts> facts, List<Finding> errors) {
    List<String> missing =
        columns.stream().filter(column -> !facts.containsKey(column)).sorted().toList();
    if (!missing.isEmpty()) {
      errors.add(
          new Finding(
              AnalysisCodes.PREGUNTA_INEXISTENTE,
              element,
              "Pregunta inexistente en el cuestionario: " + String.join(", ", missing) + ".",
              missing));
    }
  }

  // ---- CICLO ----

  private static void checkCycles(RuleSet rules, List<Finding> errors) {
    Map<String, Set<String>> graph = new TreeMap<>();
    for (SkipRule rule : rules.skips()) {
      for (String trigger : triggersOf(rule)) {
        graph.computeIfAbsent(trigger, key -> new TreeSet<>()).addAll(targetsOf(rule));
      }
    }
    Set<String> reported = new HashSet<>();
    for (String start : graph.keySet()) {
      if (reported.contains(start)) {
        continue;
      }
      List<String> cycle = cycleThrough(start, graph);
      if (!cycle.isEmpty()) {
        // Un componente se informa una sola vez, desde su código menor.
        reported.addAll(componentOf(start, graph));
        reportCycle(rules, cycle, errors);
      }
    }
  }

  /** El ciclo más corto que vuelve a {@code start}, o vacío si {@code start} no está en uno. */
  private static List<String> cycleThrough(String start, Map<String, Set<String>> graph) {
    Map<String, String> parent = new HashMap<>();
    List<String> queue = new ArrayList<>(List.of(start));
    for (int index = 0; index < queue.size(); index++) {
      String current = queue.get(index);
      for (String next : graph.getOrDefault(current, Set.of())) {
        if (next.equals(start)) {
          List<String> path = new ArrayList<>();
          for (String node = current; node != null; node = parent.get(node)) {
            path.add(0, node);
          }
          return path;
        }
        if (parent.putIfAbsent(next, current) == null) {
          queue.add(next);
        }
      }
    }
    return List.of();
  }

  /** Los nodos que {@code start} alcanza y que a su vez lo alcanzan. */
  private static Set<String> componentOf(String start, Map<String, Set<String>> graph) {
    Set<String> forward = reachable(start, graph);
    Map<String, Set<String>> reversed = new HashMap<>();
    graph.forEach(
        (from, targets) ->
            targets.forEach(to -> reversed.computeIfAbsent(to, key -> new HashSet<>()).add(from)));
    forward.retainAll(reachable(start, reversed));
    return forward;
  }

  private static Set<String> reachable(String start, Map<String, Set<String>> graph) {
    Set<String> seen = new HashSet<>();
    List<String> pending = new ArrayList<>(List.of(start));
    while (!pending.isEmpty()) {
      String current = pending.remove(pending.size() - 1);
      for (String next : graph.getOrDefault(current, Set.of())) {
        if (seen.add(next)) {
          pending.add(next);
        }
      }
    }
    return seen;
  }

  private static void reportCycle(RuleSet rules, List<String> cycle, List<Finding> errors) {
    String path = String.join(" -> ", cycle) + " -> " + cycle.get(0);
    for (SkipRule rule : rules.skips()) {
      Set<String> triggers = triggersOf(rule);
      Set<String> targets = targetsOf(rule);
      for (int index = 0; index < cycle.size(); index++) {
        String from = cycle.get(index);
        String to = cycle.get((index + 1) % cycle.size());
        if (triggers.contains(from) && targets.contains(to)) {
          errors.add(
              new Finding(
                  AnalysisCodes.CICLO,
                  "regla:" + rule.id(),
                  "La regla forma un ciclo de saltos: " + path + ".",
                  cycle));
          break;
        }
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
      if (!badValues.isEmpty()) {
        List<String> details = new ArrayList<>();
        badValues.forEach(
            (column, values) -> details.add(column + " (" + String.join(", ", values) + ")"));
        errors.add(
            new Finding(
                AnalysisCodes.VALOR_NO_ES_OPCION,
                "regla:" + rule.id(),
                "El valor no es una opción de la pregunta: " + String.join("; ", details) + ".",
                List.copyOf(badValues.keySet())));
      }
    }
  }

  private static void collectBadValues(
      Condition condition, Map<String, QuestionFacts> facts, Map<String, Set<String>> bad) {
    forEachNested(condition, child -> collectBadValues(child, facts, bad));
    if (!VALUE_OPERATORS.contains(condition.op())) {
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

  private static Set<String> triggersOf(SkipRule rule) {
    Set<String> columns = new LinkedHashSet<>();
    if (rule.when() != null) {
      collectLeafColumns(rule.when(), columns);
    }
    return columns;
  }

  private static Set<String> targetsOf(SkipRule rule) {
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
