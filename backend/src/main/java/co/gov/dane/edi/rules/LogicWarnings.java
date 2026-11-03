package co.gov.dane.edi.rules;

import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.InputType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Avisos de lógica (spec §5, avisos 1 a 5). Enumera las combinaciones de respuestas de los
 * disparadores relevantes y las evalúa con el mismo motor del encuestado, de modo que las cascadas
 * se comportan igual. Si el producto de dominios supera {@link RuleSetAnalyzer#MAX_COMBINATIONS}
 * emite {@code NO_ANALIZABLE} en vez de enumerar; lo mismo cuando se agota el presupuesto total de
 * evaluaciones de la llamada. Si el análisis trae un error {@code ESTRUCTURA}, el llamador omite la
 * enumeración por completo.
 */
final class LogicWarnings {

  private static final String UNANSWERED = "";
  private static final String ARBITRARY_TEXT = "x";

  private final RuleSet rules;
  private final Map<String, QuestionFacts> facts;
  private final Set<String> cyclicRuleIds;
  private final List<SkipRule> skips;
  private final Map<String, Set<String>> literalsByColumn = new HashMap<>();
  private final Map<String, Finding> findings = new LinkedHashMap<>();
  private final Map<String, Set<String>> notAnalyzable = new TreeMap<>();
  private final Map<String, Set<String>> overlapPartners = new TreeMap<>();
  private final Map<String, Set<String>> overlapShared = new HashMap<>();
  private final Map<String, Set<String>> overlapColumns = new HashMap<>();
  private long remainingEvaluations;

  /**
   * @param rules reglas efectivas, sin problemas estructurales (el llamador lo garantiza)
   * @param cyclicRuleIds reglas que ya tienen {@code CICLO}: no se enumeran ni se reportan
   */
  LogicWarnings(RuleSet rules, Map<String, QuestionFacts> facts, Set<String> cyclicRuleIds) {
    this(rules, facts, cyclicRuleIds, RuleSetAnalyzer.MAX_TOTAL_EVALUATIONS);
  }

  /** Con presupuesto total de evaluaciones propio: lo usan las pruebas. */
  LogicWarnings(
      RuleSet rules,
      Map<String, QuestionFacts> facts,
      Set<String> cyclicRuleIds,
      long totalEvaluations) {
    this.remainingEvaluations = totalEvaluations;
    this.rules = rules;
    this.facts = facts;
    this.cyclicRuleIds = cyclicRuleIds;
    this.skips = rules.skips();
    skips.forEach(rule -> collectLiterals(rule.when()));
  }

  /** Avisos que no necesitan enumerar: se calculan aunque la estructura tenga problemas. */
  static List<Finding> structural(RuleSet rules, Map<String, QuestionFacts> facts) {
    List<Finding> found = new ArrayList<>();
    for (SkipRule rule : rules.skips()) {
      QuestionFacts first = null;
      for (String trigger : RuleSetAnalyzer.triggersOf(rule)) {
        QuestionFacts candidate = facts.get(trigger);
        if (candidate != null && (first == null || candidate.position() < first.position())) {
          first = candidate;
        }
      }
      if (first == null) {
        continue;
      }
      List<String> before = new ArrayList<>();
      for (String target : new TreeSet<>(RuleSetAnalyzer.targetsOf(rule))) {
        QuestionFacts candidate = facts.get(target);
        if (candidate != null && candidate.position() < first.position()) {
          before.add(target);
        }
      }
      if (!before.isEmpty()) {
        found.add(
            new Finding(
                AnalysisCodes.DESTINO_ANTES,
                "regla:" + rule.id(),
                "La regla deshabilita preguntas que están antes de su disparador "
                    + first.column()
                    + ": "
                    + String.join(", ", before)
                    + ".",
                before));
      }
    }
    facts.values().stream()
        .filter(question -> question.columnStatus() == ColumnStatus.PENDING_COLUMN)
        .forEach(
            question ->
                found.add(
                    new Finding(
                        AnalysisCodes.PENDIENTE_COLUMNA,
                        "pregunta:" + question.column(),
                        "La columna de " + question.column() + " aún no existe en la tabla.",
                        List.of(question.column()))));
    return found;
  }

  /**
   * Avisos por enumeración. El orden es determinista (preguntas por código, reglas por id, pares
   * por (a, b)) para que el presupuesto total se agote siempre en los mismos elementos.
   */
  List<Finding> enumerated() {
    findings.clear();
    Set<String> targets = new TreeSet<>();
    Set<String> cyclicTargets = new TreeSet<>();
    for (SkipRule rule : skips) {
      (cyclicRuleIds.contains(rule.id()) ? cyclicTargets : targets)
          .addAll(RuleSetAnalyzer.targetsOf(rule));
    }
    targets.removeAll(cyclicTargets);
    targets.forEach(this::checkNeverShown);
    List<SkipRule> analyzable =
        skips.stream()
            .filter(rule -> !cyclicRuleIds.contains(rule.id()))
            .sorted(Comparator.comparing(SkipRule::id))
            .toList();
    analyzable.forEach(this::checkNeverMatches);
    for (int first = 0; first < analyzable.size(); first++) {
      for (int second = first + 1; second < analyzable.size(); second++) {
        checkOverlap(analyzable.get(first), analyzable.get(second));
      }
    }
    emitOverlaps();
    notAnalyzable.forEach(
        (element, columns) ->
            add(
                AnalysisCodes.NO_ANALIZABLE,
                element,
                "Hay demasiadas combinaciones de "
                    + String.join(", ", columns)
                    + " para analizar (más de "
                    + RuleSetAnalyzer.MAX_COMBINATIONS
                    + " por elemento o el presupuesto total se agotó).",
                List.copyOf(columns)));
    return List.copyOf(findings.values());
  }

  // ---- NUNCA_SE_MUESTRA ----

  private void checkNeverShown(String question) {
    Set<String> seeds = new TreeSet<>();
    skips.stream()
        .filter(rule -> RuleSetAnalyzer.targetsOf(rule).contains(question))
        .forEach(rule -> seeds.addAll(RuleSetAnalyzer.triggersOf(rule)));
    List<String> columns = closure(seeds);
    List<String> involved =
        Stream.concat(columns.stream(), Stream.of(question)).distinct().sorted().toList();
    String element = "pregunta:" + question;
    boolean[] shownSomewhere = {false};
    boolean analyzed =
        enumerate(
            columns,
            combination -> {
              if (!RuleEvaluator.evaluate(rules, combination, List.of())
                  .disabled()
                  .contains(question)) {
                shownSomewhere[0] = true;
                return false;
              }
              return true;
            });
    if (!analyzed) {
      markNotAnalyzable(element, involved);
    } else if (!shownSomewhere[0]) {
      add(
          AnalysisCodes.NUNCA_SE_MUESTRA,
          element,
          "La pregunta " + question + " siempre queda deshabilitada por las reglas.",
          involved);
    }
  }

  // ---- NUNCA_SE_CUMPLE ----

  private void checkNeverMatches(SkipRule rule) {
    List<String> columns = closure(RuleSetAnalyzer.triggersOf(rule));
    String element = "regla:" + rule.id();
    boolean[] matchesSomewhere = {false};
    boolean analyzed =
        enumerate(
            columns,
            combination -> {
              Evaluation evaluation = RuleEvaluator.evaluate(rules, combination, List.of());
              if (matchesAfterCascades(rule, combination, evaluation)) {
                matchesSomewhere[0] = true;
                return false;
              }
              return true;
            });
    if (!analyzed) {
      markNotAnalyzable(element, columns);
    } else if (!matchesSomewhere[0]) {
      add(
          AnalysisCodes.NUNCA_SE_CUMPLE,
          element,
          "Ninguna combinación de respuestas cumple la condición de la regla.",
          columns);
    }
  }

  // ---- REGLAS_SOLAPADAS ----

  /** {@code one} y {@code other} llegan en orden de id: el aviso es de la primera. */
  private void checkOverlap(SkipRule first, SkipRule second) {
    Set<String> shared = new TreeSet<>(RuleSetAnalyzer.targetsOf(first));
    shared.retainAll(RuleSetAnalyzer.targetsOf(second));
    if (shared.isEmpty()) {
      return;
    }
    Set<String> seeds = new TreeSet<>(RuleSetAnalyzer.triggersOf(first));
    seeds.addAll(RuleSetAnalyzer.triggersOf(second));
    List<String> columns = closure(seeds);
    String element = "regla:" + first.id();
    boolean[] both = {false};
    boolean analyzed =
        enumerate(
            columns,
            combination -> {
              Evaluation evaluation = RuleEvaluator.evaluate(rules, combination, List.of());
              if (matchesAfterCascades(first, combination, evaluation)
                  && matchesAfterCascades(second, combination, evaluation)) {
                both[0] = true;
                return false;
              }
              return true;
            });
    if (!analyzed) {
      markNotAnalyzable(element, columns);
    } else if (both[0]) {
      overlapPartners.computeIfAbsent(first.id(), key -> new TreeSet<>()).add(second.id());
      overlapShared.computeIfAbsent(first.id(), key -> new TreeSet<>()).addAll(shared);
      overlapColumns.computeIfAbsent(first.id(), key -> new TreeSet<>()).addAll(seeds);
    }
  }

  /** Un solo aviso por regla de menor id, con todos sus socios y la unión de columnas. */
  private void emitOverlaps() {
    overlapPartners.forEach(
        (ruleId, partners) -> {
          Set<String> columns = new TreeSet<>(overlapColumns.get(ruleId));
          columns.addAll(overlapShared.get(ruleId));
          add(
              AnalysisCodes.REGLAS_SOLAPADAS,
              "regla:" + ruleId,
              "La regla se solapa con "
                  + String.join(", ", partners)
                  + " sobre: "
                  + String.join(", ", overlapShared.get(ruleId))
                  + ".",
              List.copyOf(columns));
        });
  }

  // ---- Enumeración ----

  private boolean matchesAfterCascades(
      SkipRule rule, Map<String, String> combination, Evaluation evaluation) {
    Map<String, String> effective = new HashMap<>(combination);
    evaluation.disabled().forEach(effective::remove);
    return RuleEvaluator.matches(rule.when(), effective);
  }

  /**
   * Recorre el producto de dominios de las columnas hasta que {@code visitor} devuelva falso, y
   * descuenta cada visita del presupuesto total. Devuelve falso, sin visitar nada, si el producto
   * supera el límite por elemento o el presupuesto que queda.
   */
  private boolean enumerate(List<String> columns, Predicate<Map<String, String>> visitor) {
    List<List<String>> domains = columns.stream().map(this::domainOf).toList();
    long product = 1;
    for (List<String> domain : domains) {
      product *= domain.size();
      if (product > RuleSetAnalyzer.MAX_COMBINATIONS || product > remainingEvaluations) {
        return false;
      }
    }
    int[] cursor = new int[columns.size()];
    while (true) {
      Map<String, String> combination = new TreeMap<>();
      for (int index = 0; index < cursor.length; index++) {
        combination.put(columns.get(index), domains.get(index).get(cursor[index]));
      }
      remainingEvaluations--;
      if (!visitor.test(combination)) {
        return true;
      }
      int position = cursor.length - 1;
      while (position >= 0 && ++cursor[position] == domains.get(position).size()) {
        cursor[position--] = 0;
      }
      if (position < 0) {
        return true;
      }
    }
  }

  private List<String> domainOf(String column) {
    QuestionFacts question = facts.get(column);
    InputType type = question == null ? InputType.TEXT : question.inputType();
    Set<String> domain = new LinkedHashSet<>();
    if (type == InputType.CHECKBOX) {
      domain.add("0");
      domain.add("1");
    } else if (type == InputType.SINGLE_CHOICE) {
      domain.addAll(question.optionValues());
      domain.add(UNANSWERED);
    } else {
      domain.add(UNANSWERED);
      for (String literal : literalsByColumn.getOrDefault(column, Set.of())) {
        domain.add(literal);
        domain.addAll(neighbours(literal));
      }
      domain.add(ARBITRARY_TEXT);
    }
    return List.copyOf(domain);
  }

  private static List<String> neighbours(String literal) {
    try {
      BigDecimal number = new BigDecimal(literal.strip());
      return List.of(
          number.subtract(BigDecimal.ONE).toPlainString(),
          number.add(BigDecimal.ONE).toPlainString());
    } catch (NumberFormatException notANumber) {
      return List.of();
    }
  }

  /** Disparadores dados, más los de las reglas que los ocultan, transitivamente. */
  private List<String> closure(Collection<String> seeds) {
    Set<String> columns = new LinkedHashSet<>(seeds);
    boolean grew = true;
    while (grew) {
      grew = false;
      for (SkipRule rule : skips) {
        if (RuleSetAnalyzer.targetsOf(rule).stream().anyMatch(columns::contains)
            && columns.addAll(RuleSetAnalyzer.triggersOf(rule))) {
          grew = true;
        }
      }
    }
    return columns.stream().sorted().toList();
  }

  private void collectLiterals(Condition condition) {
    if (condition == null) {
      return;
    }
    if (condition.op() != null) {
      List<String> values = new ArrayList<>();
      if (condition.value() != null) {
        values.add(condition.value());
      }
      if (condition.values() != null) {
        condition.values().stream().filter(Objects::nonNull).forEach(values::add);
      }
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
      for (String column : columns) {
        if (column != null) {
          literalsByColumn.computeIfAbsent(column, key -> new TreeSet<>()).addAll(values);
        }
      }
    }
    if (condition.and() != null) {
      condition.and().forEach(this::collectLiterals);
    }
    if (condition.or() != null) {
      condition.or().forEach(this::collectLiterals);
    }
  }

  /** Un solo NO_ANALIZABLE por elemento: se fusionan las columnas de todos sus análisis. */
  private void markNotAnalyzable(String element, Collection<String> columns) {
    notAnalyzable.computeIfAbsent(element, key -> new TreeSet<>()).addAll(columns);
  }

  /** Un solo aviso por (código, elemento, preguntas): el mismo par no se reporta dos veces. */
  private void add(String code, String element, String message, List<String> questions) {
    Finding finding = new Finding(code, element, message, questions);
    findings.putIfAbsent(code + "|" + element + "|" + finding.questions(), finding);
  }
}
