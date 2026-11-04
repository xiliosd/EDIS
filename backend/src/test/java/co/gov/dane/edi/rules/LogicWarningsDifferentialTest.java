package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.InputType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Compara el análisis con dominios reducidos contra la enumeración de todas las respuestas
 * candidatas, en conjuntos de reglas aleatorios pero deterministas (semilla fija).
 */
class LogicWarningsDifferentialTest {

  private static final long SEED = 20261103L;
  private static final int RULE_SETS = 2000;
  private static final long AMPLE_WORK = Long.MAX_VALUE / 4;
  private static final List<String> COLUMNS = List.of("C1", "C2", "C3", "C4", "C5", "C6");
  private static final List<String> OPERATORS =
      List.of("eq", "ne", "in", "notIn", "gt", "lt", "empty", "notEmpty");
  private static final List<String> LITERALS =
      List.of("1", "2", "9", "0", "5", "", "a", " 2", "1.5", "-1", "x");
  private static final Set<String> COMPARED_CODES =
      Set.of(
          AnalysisCodes.NUNCA_SE_MUESTRA,
          AnalysisCodes.NUNCA_SE_CUMPLE,
          AnalysisCodes.REGLAS_SOLAPADAS);

  @Test
  void reducedDomainsGiveTheSameWarningsAsFullEnumeration() {
    Map<String, QuestionFacts> facts = facts();
    Random random = new Random(SEED);
    int comparedWarnings = 0;
    for (int iteration = 0; iteration < RULE_SETS; iteration++) {
      RuleSet set =
          new RuleSet("EDIN", 2026, 1, List.of(), Map.of(), null, Map.of())
              .withSkips(randomRules(random));
      List<Finding> full = new LogicWarnings(set, facts, Set.of(), AMPLE_WORK, false).enumerated();
      List<Finding> reduced =
          new LogicWarnings(set, facts, Set.of(), AMPLE_WORK, true).enumerated();
      // Lo que la enumeración completa no alcanza a analizar no se puede comparar.
      Set<String> fullNotAnalyzable = notAnalyzableElements(full);
      assertThat(fullNotAnalyzable)
          .as("conjunto %d: %s", iteration, set.skips())
          .containsAll(notAnalyzableElements(reduced));
      Set<String> expected = comparable(full, fullNotAnalyzable);
      assertThat(comparable(reduced, fullNotAnalyzable))
          .as("conjunto %d: %s", iteration, set.skips())
          .isEqualTo(expected);
      comparedWarnings += expected.size();
    }
    // La comparación solo vale si los conjuntos aleatorios producen avisos.
    assertThat(comparedWarnings).isGreaterThan(RULE_SETS / 2);
  }

  private static Map<String, QuestionFacts> facts() {
    Map<String, QuestionFacts> facts = new LinkedHashMap<>();
    facts.put("C1", question("C1", 0, InputType.SINGLE_CHOICE, "1", "2", "9"));
    facts.put("C2", question("C2", 1, InputType.CHECKBOX, "0", "1"));
    facts.put("C3", question("C3", 2, InputType.TEXT));
    facts.put("C4", question("C4", 3, InputType.SINGLE_CHOICE, "1", "2"));
    facts.put("C5", question("C5", 4, InputType.TEXT));
    facts.put("C6", question("C6", 5, InputType.CHECKBOX, "0", "1"));
    return facts;
  }

  private static QuestionFacts question(
      String column, int position, InputType type, String... options) {
    return new QuestionFacts(
        column, "P1", position, type, List.of(options), ColumnStatus.ACTIVE, null);
  }

  private static List<SkipRule> randomRules(Random random) {
    List<SkipRule> skips = new ArrayList<>();
    int ruleCount = 1 + random.nextInt(4);
    for (int index = 0; index < ruleCount; index++) {
      Condition when;
      if (random.nextInt(3) == 0) {
        List<Condition> children = new ArrayList<>();
        int childCount = 2 + random.nextInt(2);
        for (int child = 0; child < childCount; child++) {
          children.add(randomLeaf(random));
        }
        when =
            random.nextBoolean()
                ? new Condition(null, null, null, null, null, null, children, null)
                : new Condition(null, null, null, null, null, null, null, children);
      } else {
        when = randomLeaf(random);
      }
      Set<String> targets = new TreeSet<>();
      int targetCount = 1 + random.nextInt(2);
      for (int target = 0; target < targetCount; target++) {
        targets.add(pickOne(random, COLUMNS));
      }
      String id = "r" + index;
      skips.add(new SkipRule(id, id, when, new SkipAction("deshabilitar", List.copyOf(targets))));
    }
    return skips;
  }

  private static Condition randomLeaf(Random random) {
    String op = pickOne(random, OPERATORS);
    String value = pickOne(random, LITERALS);
    List<String> values = null;
    if (op.equals("in") || op.equals("notIn")) {
      values = new ArrayList<>();
      int valueCount = 1 + random.nextInt(3);
      for (int index = 0; index < valueCount; index++) {
        values.add(pickOne(random, LITERALS));
      }
      value = null;
    }
    if (op.equals("empty") || op.equals("notEmpty")) {
      value = null;
    }
    int shape = random.nextInt(4);
    if (shape == 0) {
      return new Condition(null, op, value, values, randomColumns(random), null, null, null);
    }
    if (shape == 1) {
      return new Condition(null, op, value, values, null, randomColumns(random), null, null);
    }
    return new Condition(pickOne(random, COLUMNS), op, value, values, null, null, null, null);
  }

  private static List<String> randomColumns(Random random) {
    Set<String> columns = new TreeSet<>();
    int columnCount = 2 + random.nextInt(2);
    for (int index = 0; index < columnCount; index++) {
      columns.add(pickOne(random, COLUMNS));
    }
    return List.copyOf(columns);
  }

  private static String pickOne(Random random, List<String> options) {
    return options.get(random.nextInt(options.size()));
  }

  private static Set<String> notAnalyzableElements(List<Finding> found) {
    return found.stream()
        .filter(finding -> finding.code().equals(AnalysisCodes.NO_ANALIZABLE))
        .map(Finding::element)
        .collect(Collectors.toSet());
  }

  private static Set<String> comparable(List<Finding> found, Set<String> skippedElements) {
    return found.stream()
        .filter(finding -> COMPARED_CODES.contains(finding.code()))
        .filter(finding -> !skippedElements.contains(finding.element()))
        .map(
            finding ->
                finding.code()
                    + "|"
                    + finding.element()
                    + "|"
                    + finding.questions()
                    + "|"
                    + finding.message())
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
