package co.gov.dane.edi.rules;

import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.InputType;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
 * se comportan igual. Si el producto de dominios de un elemento supera {@link
 * RuleSetAnalyzer#MAX_COMBINATIONS} emite {@code NO_ANALIZABLE} para ese elemento. El trabajo total
 * de la llamada está acotado por un presupuesto en unidades (una regla evaluada en una
 * combinación); cuando se agota, los elementos que faltan se resumen en un solo {@code
 * NO_ANALIZABLE} del archivo. Si el análisis trae un error {@code ESTRUCTURA}, el llamador omite la
 * enumeración por completo.
 */
final class LogicWarnings {

  private static final String UNANSWERED = "";
  private static final String ARBITRARY_TEXT = "x";
  private static final String FILE_ELEMENT = "archivo";

  /**
   * Columnas (o reglas) que nombra como máximo un aviso, en su orden determinista; el mensaje
   * resume el resto con «y N más». Sin tope, una cadena de R reglas daría avisos de tamaño R².
   */
  static final int MAX_NAMED = 20;

  private final RuleSet rules;
  private final Map<String, QuestionFacts> facts;
  private final Set<String> cyclicRuleIds;
  private final List<SkipRule> skips;
  private final Map<SkipRule, Set<String>> triggersByRule = new IdentityHashMap<>();
  private final Map<SkipRule, Set<String>> targetsByRule = new IdentityHashMap<>();
  private final Map<String, List<SkipRule>> rulesByTarget = new HashMap<>();
  private final Map<String, Set<String>> ancestorsByColumn = new HashMap<>();
  private final Map<String, List<String>> domainByColumn = new HashMap<>();
  private final Map<String, Set<String>> literalsByColumn = new HashMap<>();
  private final Map<String, List<Condition>> leavesByColumn = new HashMap<>();
  private final Map<String, Finding> findings = new LinkedHashMap<>();
  private final Map<String, Set<String>> notAnalyzable = new TreeMap<>();
  private final Set<String> unanalyzed = new TreeSet<>();
  private final Map<String, Set<String>> overlapPartners = new TreeMap<>();
  private final Map<String, Set<String>> overlapShared = new HashMap<>();
  private final Map<String, Set<String>> overlapColumns = new HashMap<>();
  private final long workPerEvaluation;
  private final boolean reduceDomains;
  private long remainingWork;
  private long reservedWork;
  private long plannedPairs;

  /** Qué se analiza de un elemento: una pregunta, una regla o un par de reglas. */
  private enum Kind {
    NEVER_SHOWN,
    NEVER_MATCHES,
    OVERLAP
  }

  /** Un análisis ya planeado: sus columnas y el producto de sus dominios, sin enumerar aún. */
  private record Check(
      Kind kind,
      String element,
      String question,
      SkipRule first,
      SkipRule second,
      Set<String> seeds,
      List<String> columns,
      long product) {}

  /**
   * @param rules reglas efectivas, sin problemas estructurales (el llamador lo garantiza)
   * @param cyclicRuleIds reglas que ya tienen {@code CICLO}: no se enumeran ni se reportan
   */
  LogicWarnings(RuleSet rules, Map<String, QuestionFacts> facts, Set<String> cyclicRuleIds) {
    this(rules, facts, cyclicRuleIds, RuleSetAnalyzer.MAX_TOTAL_WORK);
  }

  /** Con presupuesto de trabajo propio: lo usan las pruebas. */
  LogicWarnings(
      RuleSet rules, Map<String, QuestionFacts> facts, Set<String> cyclicRuleIds, long totalWork) {
    this(rules, facts, cyclicRuleIds, totalWork, true);
  }

  /**
   * Con {@code reduceDomains} falso enumera cada respuesta candidata sin agruparlas: la prueba
   * diferencial lo usa para comprobar que la reducción de dominios no cambia ningún aviso.
   */
  LogicWarnings(
      RuleSet rules,
      Map<String, QuestionFacts> facts,
      Set<String> cyclicRuleIds,
      long totalWork,
      boolean reduceDomains) {
    this.reduceDomains = reduceDomains;
    this.remainingWork = totalWork;
    this.rules = rules;
    this.facts = facts;
    this.cyclicRuleIds = cyclicRuleIds;
    this.skips = rules.skips();
    this.workPerEvaluation = Math.max(1, skips.size());
    // Una columna que no es pregunta ya es PREGUNTA_INEXISTENTE: no entra en dominios, clausuras
    // ni enumeración, para que un archivo no pueda inflar el análisis con columnas inventadas.
    for (SkipRule rule : skips) {
      collectLiterals(rule.when());
      triggersByRule.put(rule, knownColumns(RuleSetAnalyzer.triggersOf(rule)));
      Set<String> targets = knownColumns(RuleSetAnalyzer.targetsOf(rule));
      targetsByRule.put(rule, targets);
      targets.forEach(
          target -> rulesByTarget.computeIfAbsent(target, key -> new ArrayList<>()).add(rule));
    }
  }

  private Set<String> knownColumns(Set<String> columns) {
    Set<String> known = new HashSet<>(columns);
    known.retainAll(facts.keySet());
    return known;
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
                    + describeNames(before)
                    + ".",
                firstNames(before)));
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
   * Avisos por enumeración. Primero se planean todos los análisis (preguntas por código, reglas por
   * id, pares por (a, b)) y luego se ejecutan de menor a mayor producto, con ese orden como
   * desempate: así el presupuesto rinde para el mayor número de elementos y se agota siempre en los
   * mismos.
   */
  List<Finding> enumerated() {
    findings.clear();
    List<Check> checks = plan();
    checks.sort(Comparator.comparingLong(Check::product));
    boolean stopped = false;
    for (Check check : checks) {
      if (exceedsLimit(check)) {
        markNotAnalyzable(check.element(), involvedColumns(check));
      } else if (stopped || check.product() * workPerEvaluation > remainingWork) {
        stopped = true;
        unanalyzed.add(check.element());
      } else {
        run(check);
      }
    }
    emitOverlaps();
    notAnalyzable.forEach(
        (element, columns) ->
            add(
                AnalysisCodes.NO_ANALIZABLE,
                element,
                "Hay demasiadas combinaciones de "
                    + describeNames(columns)
                    + " para analizar (más de "
                    + RuleSetAnalyzer.MAX_COMBINATIONS
                    + " por elemento).",
                List.copyOf(columns)));
    if (!unanalyzed.isEmpty()) {
      add(
          AnalysisCodes.NO_ANALIZABLE,
          FILE_ELEMENT,
          "El análisis de lógica se detuvo por tamaño; "
              + unanalyzed.size()
              + " elementos quedaron sin analizar.",
          List.of());
    }
    return List.copyOf(findings.values());
  }

  /**
   * Planea los análisis en el orden de desempate. Planear también gasta presupuesto (una unidad por
   * columna) y cada par reserva además una evaluación: si se agota aquí, los pares que faltan
   * quedan sin analizar sin calcular ni guardar nada más.
   */
  private List<Check> plan() {
    reservedWork = 0;
    plannedPairs = 0;
    List<Check> checks = new ArrayList<>();
    Set<String> targets = new TreeSet<>();
    Set<String> cyclicTargets = new TreeSet<>();
    for (SkipRule rule : skips) {
      (cyclicRuleIds.contains(rule.id()) ? cyclicTargets : targets).addAll(targetsByRule.get(rule));
    }
    targets.removeAll(cyclicTargets);
    for (String question : targets) {
      Set<String> seeds = new TreeSet<>();
      rulesByTarget.get(question).forEach(rule -> seeds.addAll(triggersByRule.get(rule)));
      checks.add(planCheck(Kind.NEVER_SHOWN, "pregunta:" + question, question, null, null, seeds));
    }
    List<SkipRule> analyzable =
        skips.stream()
            .filter(rule -> !cyclicRuleIds.contains(rule.id()))
            .sorted(Comparator.comparing(SkipRule::id))
            .toList();
    Map<SkipRule, Check> ownChecks = new IdentityHashMap<>();
    for (SkipRule rule : analyzable) {
      Check own =
          planCheck(
              Kind.NEVER_MATCHES, "regla:" + rule.id(), null, rule, null, triggersByRule.get(rule));
      ownChecks.put(rule, own);
      checks.add(own);
    }
    planOverlaps(analyzable, ownChecks, checks);
    return checks;
  }

  /**
   * Planea los pares (a, b) con a antes que b que comparten algún destino; los socios salen del
   * índice por destino, sin recorrer todos los pares. Cada par reserva una evaluación, lo mínimo
   * que gastará al ejecutarse, de modo que nunca se guardan más pares de los que el presupuesto
   * puede pagar (unos {@code MAX_TOTAL_WORK / workPerEvaluation}). Es una reserva y no un cobro: si
   * se descontara, planear muchos pares dejaría sin presupuesto a las preguntas y reglas, más
   * baratas. Si una de las dos reglas ya supera el tope por sí sola, el par tampoco cabe (sus
   * columnas incluyen las de ambas): se marca sin calcular sus columnas ni guardarlo.
   */
  private void planOverlaps(
      List<SkipRule> analyzable, Map<SkipRule, Check> ownChecks, List<Check> checks) {
    Map<String, List<Integer>> positionsByTarget = new HashMap<>();
    for (int position = 0; position < analyzable.size(); position++) {
      for (String target : targetsByRule.get(analyzable.get(position))) {
        positionsByTarget.computeIfAbsent(target, key -> new ArrayList<>()).add(position);
      }
    }
    int[] collectedFor = new int[analyzable.size()];
    Arrays.fill(collectedFor, -1);
    boolean[] markedOwn = new boolean[analyzable.size()];
    for (int first = 0; first < analyzable.size(); first++) {
      SkipRule one = analyzable.get(first);
      List<Integer> partners = new ArrayList<>();
      for (String target : targetsByRule.get(one)) {
        for (int position : positionsByTarget.get(target)) {
          if (position > first && collectedFor[position] != first) {
            collectedFor[position] = first;
            partners.add(position);
          }
        }
      }
      Collections.sort(partners);
      String element = "regla:" + one.id();
      Check ownOne = ownChecks.get(one);
      boolean oneUnanalyzed = false;
      for (int position : partners) {
        SkipRule other = analyzable.get(position);
        Check ownOther = ownChecks.get(other);
        if (exceedsLimit(ownOne) || exceedsLimit(ownOther)) {
          // El solape de a queda sin analizar: se marca a (y b si es ella la que no cabe) una sola
          // vez y con sus propias columnas, nunca con la unión de sus socios, que crecería con el
          // número de reglas.
          markOwnNotAnalyzable(ownOne, markedOwn, first);
          if (exceedsLimit(ownOther)) {
            markOwnNotAnalyzable(ownOther, markedOwn, position);
          }
        } else if (remainingWork - reservedWork < workPerEvaluation) {
          if (!oneUnanalyzed) {
            unanalyzed.add(element);
            oneUnanalyzed = true;
          }
        } else {
          Set<String> seeds = new TreeSet<>(triggersByRule.get(one));
          seeds.addAll(triggersByRule.get(other));
          checks.add(planCheck(Kind.OVERLAP, element, null, one, other, seeds));
          reservedWork += workPerEvaluation;
          plannedPairs++;
        }
      }
    }
  }

  private void markOwnNotAnalyzable(Check own, boolean[] markedOwn, int position) {
    if (!markedOwn[position]) {
      markedOwn[position] = true;
      markNotAnalyzable(own.element(), own.columns());
    }
  }

  /** Pares de solape guardados en la última planificación: lo usan las pruebas. */
  long plannedPairs() {
    return plannedPairs;
  }

  private static boolean exceedsLimit(Check check) {
    return check.product() > RuleSetAnalyzer.MAX_COMBINATIONS;
  }

  private Check planCheck(
      Kind kind,
      String element,
      String question,
      SkipRule first,
      SkipRule second,
      Set<String> seeds) {
    List<String> columns = closure(seeds);
    remainingWork -= columns.size();
    long product = 1;
    for (String column : columns) {
      product *= domainOf(column).size();
      if (product > RuleSetAnalyzer.MAX_COMBINATIONS) {
        break;
      }
    }
    return new Check(kind, element, question, first, second, seeds, columns, product);
  }

  private void run(Check check) {
    switch (check.kind()) {
      case NEVER_SHOWN -> checkNeverShown(check);
      case NEVER_MATCHES -> checkNeverMatches(check);
      case OVERLAP -> checkOverlap(check);
      default -> throw new IllegalStateException(check.kind().name());
    }
  }

  private static List<String> involvedColumns(Check check) {
    if (check.question() == null) {
      return check.columns();
    }
    return Stream.concat(check.columns().stream(), Stream.of(check.question()))
        .distinct()
        .sorted()
        .toList();
  }

  // ---- NUNCA_SE_MUESTRA ----

  private void checkNeverShown(Check check) {
    String question = check.question();
    boolean[] shownSomewhere = {false};
    enumerate(
        check.columns(),
        combination -> {
          if (!RuleEvaluator.evaluate(rules, combination, List.of())
              .disabled()
              .contains(question)) {
            shownSomewhere[0] = true;
            return false;
          }
          return true;
        });
    if (!shownSomewhere[0]) {
      add(
          AnalysisCodes.NUNCA_SE_MUESTRA,
          check.element(),
          "La pregunta " + question + " siempre queda deshabilitada por las reglas.",
          involvedColumns(check));
    }
  }

  // ---- NUNCA_SE_CUMPLE ----

  private void checkNeverMatches(Check check) {
    SkipRule rule = check.first();
    boolean[] matchesSomewhere = {false};
    enumerate(
        check.columns(),
        combination -> {
          Evaluation evaluation = RuleEvaluator.evaluate(rules, combination, List.of());
          if (matchesAfterCascades(rule, combination, evaluation)) {
            matchesSomewhere[0] = true;
            return false;
          }
          return true;
        });
    if (!matchesSomewhere[0]) {
      add(
          AnalysisCodes.NUNCA_SE_CUMPLE,
          check.element(),
          "Ninguna combinación de respuestas cumple la condición de la regla.",
          check.columns());
    }
  }

  // ---- REGLAS_SOLAPADAS ----

  /** Las reglas del par llegan en orden de id: el aviso es de la primera. */
  private void checkOverlap(Check check) {
    SkipRule first = check.first();
    SkipRule second = check.second();
    boolean[] both = {false};
    enumerate(
        check.columns(),
        combination -> {
          Evaluation evaluation = RuleEvaluator.evaluate(rules, combination, List.of());
          if (matchesAfterCascades(first, combination, evaluation)
              && matchesAfterCascades(second, combination, evaluation)) {
            both[0] = true;
            return false;
          }
          return true;
        });
    if (both[0]) {
      Set<String> shared = new TreeSet<>(targetsByRule.get(first));
      shared.retainAll(targetsByRule.get(second));
      overlapPartners.computeIfAbsent(first.id(), key -> new TreeSet<>()).add(second.id());
      overlapShared.computeIfAbsent(first.id(), key -> new TreeSet<>()).addAll(shared);
      overlapColumns.computeIfAbsent(first.id(), key -> new TreeSet<>()).addAll(check.seeds());
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
                  + describeNames(partners)
                  + " sobre: "
                  + describeNames(overlapShared.get(ruleId))
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
   * descuenta cada visita del presupuesto (una unidad por regla evaluada). El llamador ya comprobó
   * que el producto cabe en el límite por elemento y en el presupuesto que queda.
   */
  private void enumerate(List<String> columns, Predicate<Map<String, String>> visitor) {
    List<List<String>> domains = columns.stream().map(this::domainOf).toList();
    int[] cursor = new int[columns.size()];
    while (true) {
      Map<String, String> combination = new TreeMap<>();
      for (int index = 0; index < cursor.length; index++) {
        combination.put(columns.get(index), domains.get(index).get(cursor[index]));
      }
      remainingWork -= workPerEvaluation;
      if (!visitor.test(combination)) {
        return;
      }
      int position = cursor.length - 1;
      while (position >= 0 && ++cursor[position] == domains.get(position).size()) {
        cursor[position--] = 0;
      }
      if (position < 0) {
        return;
      }
    }
  }

  private List<String> domainOf(String column) {
    return domainByColumn.computeIfAbsent(column, this::buildDomain);
  }

  private List<String> buildDomain(String column) {
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
    return reduceDomains ? distinguishable(column, domain) : List.copyOf(domain);
  }

  /**
   * Deja un representante por cada grupo de respuestas que todas las condiciones sobre la columna
   * tratan igual. El motor solo mira la columna a través de esas condiciones, así que el resultado
   * del análisis no cambia y el producto de dominios se reduce (por ejemplo, con solo {@code eq 2}
   * basta "2" y otra respuesta cualquiera).
   */
  private List<String> distinguishable(String column, Collection<String> candidates) {
    List<Condition> leaves =
        leavesByColumn.getOrDefault(column, List.of()).stream()
            .map(
                leaf ->
                    new Condition(
                        column, leaf.op(), leaf.value(), leaf.values(), null, null, null, null))
            .toList();
    Map<List<Boolean>, String> representatives = new LinkedHashMap<>();
    for (String value : candidates) {
      Map<String, String> answer = Map.of(column, value);
      List<Boolean> signature =
          leaves.stream().map(leaf -> RuleEvaluator.matches(leaf, answer)).toList();
      representatives.putIfAbsent(signature, value);
    }
    return List.copyOf(representatives.values());
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
    Set<String> columns = new TreeSet<>();
    seeds.forEach(seed -> columns.addAll(ancestorsOf(seed)));
    return List.copyOf(columns);
  }

  /** La columna y los disparadores de todo lo que puede ocultarla, en cascada; se memoriza. */
  private Set<String> ancestorsOf(String column) {
    Set<String> known = ancestorsByColumn.get(column);
    if (known != null) {
      return known;
    }
    Set<String> reached = new HashSet<>();
    Deque<String> pending = new ArrayDeque<>();
    reached.add(column);
    pending.add(column);
    while (!pending.isEmpty()) {
      for (SkipRule rule : rulesByTarget.getOrDefault(pending.poll(), List.of())) {
        for (String trigger : triggersByRule.get(rule)) {
          if (reached.add(trigger)) {
            pending.add(trigger);
          }
        }
      }
    }
    ancestorsByColumn.put(column, reached);
    return reached;
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
        if (column != null && facts.containsKey(column)) {
          literalsByColumn.computeIfAbsent(column, key -> new TreeSet<>()).addAll(values);
          leavesByColumn.computeIfAbsent(column, key -> new ArrayList<>()).add(condition);
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

  /** Las primeras {@link #MAX_NAMED}, en el orden recibido. */
  static List<String> firstNames(Collection<String> names) {
    return names.stream().limit(MAX_NAMED).toList();
  }

  /** «A, B, C» o, si son más de {@link #MAX_NAMED}, las primeras seguidas de «y N más». */
  static String describeNames(Collection<String> names) {
    String listed = String.join(", ", firstNames(names));
    int rest = names.size() - MAX_NAMED;
    return rest > 0 ? listed + " y " + rest + " más" : listed;
  }

  /** Un solo NO_ANALIZABLE por elemento: se fusionan las columnas de todos sus análisis. */
  private void markNotAnalyzable(String element, Collection<String> columns) {
    notAnalyzable.computeIfAbsent(element, key -> new TreeSet<>()).addAll(columns);
  }

  /** Un solo aviso por (código, elemento, preguntas): el mismo par no se reporta dos veces. */
  private void add(String code, String element, String message, List<String> questions) {
    Finding finding = new Finding(code, element, message, firstNames(questions));
    findings.putIfAbsent(code + "|" + element + "|" + finding.questions(), finding);
  }
}
