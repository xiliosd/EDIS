package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.InputType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleSetAnalyzerWarningsTest {

  private static QuestionFacts single(String column, int position, String... options) {
    return new QuestionFacts(
        column,
        "P1",
        position,
        InputType.SINGLE_CHOICE,
        List.of(options),
        ColumnStatus.ACTIVE,
        null);
  }

  private static QuestionFacts checkbox(String column, int position) {
    return new QuestionFacts(
        column, "P1", position, InputType.CHECKBOX, List.of("0", "1"), ColumnStatus.ACTIVE, null);
  }

  private static QuestionFacts text(String column, int position) {
    return new QuestionFacts(
        column, "P1", position, InputType.TEXT, List.of(), ColumnStatus.ACTIVE, null);
  }

  private static Map<String, QuestionFacts> facts(QuestionFacts... questions) {
    Map<String, QuestionFacts> map = new LinkedHashMap<>();
    for (QuestionFacts question : questions) {
      map.put(question.column(), question);
    }
    return map;
  }

  private static Condition leaf(String question, String op, String value) {
    return new Condition(question, op, value, null, null, null, null, null);
  }

  private static Condition eq(String question, String value) {
    return leaf(question, "eq", value);
  }

  private static Condition and(Condition... children) {
    return new Condition(null, null, null, null, null, null, List.of(children), null);
  }

  private static SkipRule rule(String id, Condition when, String... targets) {
    return new SkipRule(id, id, when, new SkipAction("deshabilitar", List.of(targets)));
  }

  private static RuleSet rules(SkipRule... skips) {
    return new RuleSet("EDIN", 2026, 1, List.of(), Map.of(), null, Map.of())
        .withSkips(List.of(skips));
  }

  private static List<String> warnings(AnalysisReport report) {
    return report.warnings().stream().map(f -> f.code() + "@" + f.element()).toList();
  }

  @Test
  void neverShown() {
    Map<String, QuestionFacts> facts = facts(single("C01A", 0, "1", "2"), text("C01B", 1));
    SkipRule equalsOne = rule("r-eq", eq("C01A", "1"), "C01B");
    SkipRule notOne = rule("r-ne", leaf("C01A", "ne", "1"), "C01B");
    SkipRule empty = rule("r-empty", leaf("C01A", "empty", null), "C01B");

    AnalysisReport hidden = RuleSetAnalyzer.analyze(rules(equalsOne, notOne, empty), facts);
    AnalysisReport control = RuleSetAnalyzer.analyze(rules(equalsOne, notOne), facts);

    assertThat(warnings(hidden)).contains("NUNCA_SE_MUESTRA@pregunta:C01B");
    assertThat(warnings(control)).doesNotContain("NUNCA_SE_MUESTRA@pregunta:C01B");
    assertThat(hidden.hasErrors()).isFalse();
  }

  @Test
  void f04ShapeBeyondLimit() {
    List<String> columns = List.of("F04A", "F04B", "F04C", "F04D", "F04E", "F04F", "F04G", "F04H");
    List<QuestionFacts> questions = new ArrayList<>();
    for (int index = 0; index < columns.size(); index++) {
      questions.add(single(columns.get(index), index, "1", "2", "9"));
    }
    questions.add(single("F04I", columns.size(), "1", "2"));
    Condition allNo = new Condition(null, "eq", "2", null, columns, null, null, null);
    Condition anyYes =
        new Condition(null, "in", null, List.of("1", "9"), null, columns, null, null);

    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(rule("f04-todas-no", allNo, "F04I"), rule("f04-alguna-si", anyYes, "F04I")),
            facts(questions.toArray(QuestionFacts[]::new)));

    assertThat(warnings(report))
        .contains(
            "NO_ANALIZABLE@pregunta:F04I",
            "NO_ANALIZABLE@regla:f04-todas-no",
            "NO_ANALIZABLE@regla:f04-alguna-si")
        .doesNotContain("NUNCA_SE_MUESTRA@pregunta:F04I");
  }

  @Test
  void ruleThatNeverMatches() {
    Map<String, QuestionFacts> facts = facts(single("C01A", 0, "1", "2"), text("C01B", 1));
    SkipRule impossible = rule("r-imposible", and(eq("C01A", "1"), eq("C01A", "2")), "C01B");
    SkipRule possible = rule("r-posible", eq("C01A", "1"), "C01B");

    AnalysisReport report = RuleSetAnalyzer.analyze(rules(impossible, possible), facts);

    assertThat(warnings(report))
        .contains("NUNCA_SE_CUMPLE@regla:r-imposible")
        .doesNotContain("NUNCA_SE_CUMPLE@regla:r-posible");
  }

  @Test
  void ruleWhoseTriggerIsAlwaysHiddenNeverMatches() {
    Map<String, QuestionFacts> facts =
        facts(single("C01A", 0, "1", "2"), single("C01B", 1, "1", "2"), text("C01C", 2));
    // C01B se oculta siempre (C01A eq 1, ne 1 o vacía); r-depende necesita C01B = 1.
    SkipRule hideWhenOne = rule("r-uno", eq("C01A", "1"), "C01B");
    SkipRule hideWhenNotOne = rule("r-otro", leaf("C01A", "ne", "1"), "C01B");
    SkipRule hideWhenEmpty = rule("r-vacio", leaf("C01A", "empty", null), "C01B");
    SkipRule dependsOnHidden = rule("r-depende", eq("C01B", "1"), "C01C");

    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(hideWhenOne, hideWhenNotOne, hideWhenEmpty, dependsOnHidden), facts);

    assertThat(warnings(report)).contains("NUNCA_SE_CUMPLE@regla:r-depende");
  }

  @Test
  void targetBeforeTrigger() {
    Map<String, QuestionFacts> facts = facts(text("C01A", 0), single("C01B", 1, "1", "2"));

    AnalysisReport report =
        RuleSetAnalyzer.analyze(rules(rule("r-atras", eq("C01B", "1"), "C01A")), facts);

    assertThat(warnings(report)).contains("DESTINO_ANTES@regla:r-atras");
    assertThat(report.warnings().get(0).questions()).contains("C01A");
  }

  @Test
  void overlappingRules() {
    Map<String, QuestionFacts> facts =
        facts(single("C01A", 0, "1", "2"), single("C01B", 1, "1", "2"), text("C01C", 2));
    SkipRule first = rule("a-primera", eq("C01A", "1"), "C01C");
    SkipRule second = rule("b-segunda", eq("C01B", "1"), "C01C");
    SkipRule disjoint = rule("c-disjunta", eq("C01A", "2"), "C01C");

    AnalysisReport report = RuleSetAnalyzer.analyze(rules(first, second, disjoint), facts);

    // El aviso va sobre la regla de menor id del par; c-disjunta solapa con b-segunda.
    assertThat(warnings(report))
        .contains("REGLAS_SOLAPADAS@regla:a-primera", "REGLAS_SOLAPADAS@regla:b-segunda")
        .doesNotContain("REGLAS_SOLAPADAS@regla:c-disjunta");
  }

  @Test
  void disjointRulesDoNotOverlap() {
    Map<String, QuestionFacts> facts = facts(single("C01A", 0, "1", "2"), text("C01C", 1));

    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(rule("r1", eq("C01A", "1"), "C01C"), rule("r2", eq("C01A", "2"), "C01C")), facts);

    assertThat(warnings(report)).noneMatch(code -> code.startsWith("REGLAS_SOLAPADAS"));
  }

  @Test
  void pendingColumnWarning() {
    Map<String, QuestionFacts> facts =
        facts(
            new QuestionFacts(
                "H01", "P1", 0, InputType.TEXT, List.of(), ColumnStatus.PENDING_COLUMN, null),
            text("H02", 1));

    AnalysisReport report = RuleSetAnalyzer.analyze(rules(), facts);

    assertThat(warnings(report)).containsExactly("PENDIENTE_COLUMNA@pregunta:H01");
  }

  @Test
  void warningsNeverBlock() {
    Map<String, QuestionFacts> facts = facts(single("C01A", 0, "1", "2"), text("C01B", 1));

    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(rule("r-imposible", and(eq("C01A", "1"), eq("C01A", "2")), "C01B")), facts);

    assertThat(report.warnings()).isNotEmpty();
    assertThat(report.hasErrors()).isFalse();
  }

  @Test
  void cyclicRulesAreNotEnumerated() {
    Map<String, QuestionFacts> facts = facts(single("C01A", 0, "1", "2"), single("C01B", 1, "1"));

    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(rule("r1", eq("C01A", "1"), "C01B"), rule("r2", eq("C01B", "1"), "C01A")), facts);

    assertThat(report.errors()).extracting(Finding::code).contains(AnalysisCodes.CICLO);
    assertThat(warnings(report)).noneMatch(code -> code.contains("NUNCA_SE_"));
    assertThat(warnings(report)).noneMatch(code -> code.startsWith("REGLAS_SOLAPADAS"));
  }

  @Test
  void textTriggerUsesLiteralsAndNeighbours() {
    Map<String, QuestionFacts> facts = facts(text("C01A", 0), text("C01B", 1));

    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(
                rule("r-mayor", leaf("C01A", "gt", "5"), "C01B"),
                rule("r-menor", leaf("C01A", "lt", "6"), "C01B")),
            facts);

    // 6 cumple la primera y 5 la segunda: los vecinos del literal bastan para verlo.
    assertThat(warnings(report))
        .doesNotContain("NUNCA_SE_CUMPLE@regla:r-mayor", "NUNCA_SE_CUMPLE@regla:r-menor");
  }

  @Test
  void analysisBeyondLimitReportsNotAnalyzableQuickly() {
    List<QuestionFacts> questions = new ArrayList<>();
    List<String> columns = new ArrayList<>();
    for (int index = 0; index < 13; index++) {
      String column = String.format("K%02d", index);
      columns.add(column);
      questions.add(checkbox(column, index));
    }
    questions.add(text("K99", 13));
    Condition anyOf = new Condition(null, "eq", "1", null, null, columns, null, null);
    RuleSet set = rules(rule("r-trece", anyOf, "K99"));
    Map<String, QuestionFacts> facts = facts(questions.toArray(QuestionFacts[]::new));

    AnalysisReport report =
        assertTimeoutPreemptively(
            Duration.ofMillis(1000), () -> RuleSetAnalyzer.analyze(set, facts));

    assertThat(warnings(report)).contains("NO_ANALIZABLE@regla:r-trece");
    assertThat(report.hasErrors()).isFalse();
  }

  @Test
  void overlapWithTwoPartnersIsOneWarning() {
    Map<String, QuestionFacts> facts =
        facts(
            single("C01A", 0, "1", "2"),
            single("C01B", 1, "1", "2"),
            single("C01D", 2, "1", "2"),
            text("C01C", 3));
    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(
                rule("a-primera", eq("C01A", "1"), "C01C"),
                rule("b-segunda", eq("C01B", "1"), "C01C"),
                rule("c-tercera", eq("C01D", "1"), "C01C")),
            facts);

    List<Finding> overlaps =
        report.warnings().stream().filter(f -> f.code().equals("REGLAS_SOLAPADAS")).toList();

    assertThat(overlaps).hasSize(2);
    Finding ofFirst = overlaps.get(0);
    assertThat(ofFirst.element()).isEqualTo("regla:a-primera");
    assertThat(ofFirst.message()).contains("b-segunda", "c-tercera");
    assertThat(ofFirst.questions()).containsExactly("C01A", "C01B", "C01C", "C01D");
  }

  @Test
  void notAnalyzableIsOnePerElementWhenOverlapAndOwnAnalysisBothExceed() {
    List<QuestionFacts> questions = new ArrayList<>();
    List<String> columns = new ArrayList<>();
    for (int index = 0; index < 13; index++) {
      String column = String.format("K%02d", index);
      columns.add(column);
      questions.add(checkbox(column, index));
    }
    questions.add(text("K99", 13));
    Condition anyOf = new Condition(null, "eq", "1", null, null, columns, null, null);
    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(rule("r-a", anyOf, "K99"), rule("r-b", anyOf, "K99")),
            facts(questions.toArray(QuestionFacts[]::new)));

    assertThat(
            report.warnings().stream()
                .filter(f -> f.code().equals("NO_ANALIZABLE") && f.element().equals("regla:r-a")))
        .hasSize(1);
  }

  @Test
  void totalBudgetMakesRemainingElementsNotAnalyzable() {
    Map<String, QuestionFacts> facts =
        facts(single("C01A", 0, "1", "2"), text("C01B", 1), text("C01C", 2));
    RuleSet set =
        rules(
            rule("r-eq", eq("C01A", "1"), "C01B", "C01C"),
            rule("r-ne", leaf("C01A", "ne", "1"), "C01B", "C01C"),
            rule("r-empty", leaf("C01A", "empty", null), "C01B", "C01C"));

    List<Finding> found = new LogicWarnings(set, facts, java.util.Set.of(), 4).enumerated();
    List<String> codes = found.stream().map(f -> f.code() + "@" + f.element()).toList();

    // C01B gasta 3 de las 4 evaluaciones; a C01C y a las reglas solo les queda 1.
    assertThat(codes)
        .contains(
            "NUNCA_SE_MUESTRA@pregunta:C01B",
            "NO_ANALIZABLE@pregunta:C01C",
            "NO_ANALIZABLE@regla:r-eq");
    assertThat(codes).doesNotContain("NUNCA_SE_MUESTRA@pregunta:C01C");
  }

  @Test
  void realisticQuestionnaireAnalyzesQuickly() {
    List<QuestionFacts> questions = new ArrayList<>();
    for (int index = 0; index < 150; index++) {
      questions.add(
          new QuestionFacts(
              String.format("Q%03d", index),
              "P" + (index / 17),
              index,
              InputType.SINGLE_CHOICE,
              List.of("1", "2", "9"),
              ColumnStatus.ACTIVE,
              null));
    }
    List<SkipRule> skips = new ArrayList<>();
    for (int chain = 0; chain < 4; chain++) {
      int base = chain * 10;
      for (int step = 0; step < 4; step++) {
        skips.add(
            rule(
                String.format("cadena-%d-%d", chain, step),
                eq(String.format("Q%03d", base + step), "1"),
                String.format("Q%03d", base + step + 1)));
      }
      skips.add(
          rule(
              String.format("cadena-%d-alterna", chain),
              eq(String.format("Q%03d", base), "2"),
              String.format("Q%03d", base + 1)));
    }
    for (int index = 0; index < 4; index++) {
      skips.add(
          rule(
              "suelta-" + index,
              eq(String.format("Q%03d", 100 + index), "1"),
              String.format("Q%03d", 120 + index)));
    }
    RuleSet set = rules(skips.toArray(SkipRule[]::new));
    Map<String, QuestionFacts> facts = facts(questions.toArray(QuestionFacts[]::new));

    AnalysisReport report =
        assertTimeoutPreemptively(
            Duration.ofMillis(2000), () -> RuleSetAnalyzer.analyze(set, facts));

    assertThat(report.hasErrors()).isFalse();
    // Mismo destino pero condiciones disjuntas (1 frente a 2): no hay solape.
    assertThat(warnings(report)).noneMatch(code -> code.startsWith("REGLAS_SOLAPADAS"));
  }

  @Test
  void neighbourValuesLetStrictComparisonsMatch() {
    Map<String, QuestionFacts> facts = facts(text("C01A", 0), text("C01B", 1));

    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(
                rule("r-igual", eq("C01A", "5"), "C01B"),
                rule("r-mayor", leaf("C01A", "gt", "5"), "C01B"),
                rule("r-menor", leaf("C01A", "lt", "5"), "C01B")),
            facts);

    // Con solo el literal 5 ni gt ni lt se cumplirían: hacen falta 6 y 4.
    assertThat(warnings(report)).noneMatch(code -> code.startsWith("NUNCA_SE_CUMPLE"));
  }

  @Test
  void arbitraryTextSentinelLetsNotEmptyMatch() {
    Map<String, QuestionFacts> facts = facts(text("C01A", 0), text("C01B", 1));

    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(rule("r-lleno", leaf("C01A", "notEmpty", null), "C01B")), facts);

    assertThat(warnings(report)).doesNotContain("NUNCA_SE_CUMPLE@regla:r-lleno");
  }

  @Test
  void twelveCheckboxesAreAnalyzableAndThirteenAreNot() {
    for (int count : new int[] {12, 13}) {
      List<QuestionFacts> questions = new ArrayList<>();
      List<String> columns = new ArrayList<>();
      for (int index = 0; index < count; index++) {
        String column = String.format("K%02d", index);
        columns.add(column);
        questions.add(checkbox(column, index));
      }
      questions.add(text("K99", count));
      Condition anyOf = new Condition(null, "eq", "1", null, null, columns, null, null);

      AnalysisReport report =
          RuleSetAnalyzer.analyze(
              rules(rule("r-borde", anyOf, "K99")), facts(questions.toArray(QuestionFacts[]::new)));

      if (count == 12) {
        assertThat(warnings(report)).doesNotContain("NO_ANALIZABLE@regla:r-borde");
      } else {
        assertThat(warnings(report)).contains("NO_ANALIZABLE@regla:r-borde");
      }
    }
  }
}
