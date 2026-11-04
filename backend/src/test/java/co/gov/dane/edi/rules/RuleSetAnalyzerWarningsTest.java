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
import java.util.Set;
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
  void exhaustedBudgetIsOneFileWarningInsteadOfOnePerElement() {
    Map<String, QuestionFacts> facts =
        facts(single("C01A", 0, "1", "2"), text("C01B", 1), text("C01C", 2));
    RuleSet set =
        rules(
            rule("r-eq", eq("C01A", "1"), "C01B", "C01C"),
            rule("r-ne", leaf("C01A", "ne", "1"), "C01B", "C01C"),
            rule("r-empty", leaf("C01A", "empty", null), "C01B", "C01C"));

    // Planear cuesta 8 (una por columna de cada análisis) y cada evaluación 3 (3 reglas):
    // C01B gasta 3 × 3 = 9 y a lo demás le quedan 3 de las 20.
    List<Finding> found = new LogicWarnings(set, facts, Set.of(), 20).enumerated();
    List<String> codes = found.stream().map(f -> f.code() + "@" + f.element()).toList();

    assertThat(codes).contains("NUNCA_SE_MUESTRA@pregunta:C01B", "NO_ANALIZABLE@archivo");
    assertThat(codes)
        .doesNotContain(
            "NUNCA_SE_MUESTRA@pregunta:C01C",
            "NO_ANALIZABLE@pregunta:C01C",
            "NO_ANALIZABLE@regla:r-eq");
    Finding stopped =
        found.stream().filter(f -> f.element().equals("archivo")).findFirst().orElseThrow();
    assertThat(stopped.questions()).isEmpty();
    // Quedan C01C y las tres reglas (sus pares de solape caen en r-empty y r-eq).
    assertThat(stopped.message())
        .isEqualTo(
            "El análisis de lógica se detuvo por tamaño; 4 elementos quedaron sin analizar.");
  }

  @Test
  void cheapElementsAreAnalyzedBeforeExpensiveOnes() {
    List<QuestionFacts> questions = new ArrayList<>();
    List<String> columns = new ArrayList<>();
    for (int index = 0; index < 10; index++) {
      String column = String.format("A%02d", index);
      columns.add(column);
      questions.add(checkbox(column, index));
    }
    questions.add(text("A99", 10));
    questions.add(single("Z01", 11, "1", "2"));
    questions.add(text("Z02", 12));
    // a-cara (1024 combinaciones) va antes por id, pero z-barata (3) cabe en el presupuesto.
    Condition anyOf = new Condition(null, "eq", "1", null, null, columns, null, null);
    RuleSet set =
        rules(
            rule("a-cara", anyOf, "A99"),
            rule("z-barata", and(eq("Z01", "1"), eq("Z01", "2")), "Z02"));

    List<Finding> found =
        new LogicWarnings(set, facts(questions.toArray(QuestionFacts[]::new)), Set.of(), 40)
            .enumerated();
    List<String> codes = found.stream().map(f -> f.code() + "@" + f.element()).toList();

    assertThat(codes).contains("NUNCA_SE_CUMPLE@regla:z-barata", "NO_ANALIZABLE@archivo");
  }

  @Test
  void answersTheRulesCannotTellApartAreEnumeratedOnce() {
    List<QuestionFacts> questions = new ArrayList<>();
    List<String> columns = new ArrayList<>();
    for (int index = 0; index < 7; index++) {
      String column = String.format("M%02d", index);
      columns.add(column);
      questions.add(single(column, index, "1", "2", "3", "9"));
    }
    questions.add(text("M99", 7));
    // 5^7 combinaciones en bruto, pero las reglas solo distinguen "2" del resto: 2^7.
    Condition allNo = new Condition(null, "eq", "2", null, columns, null, null, null);

    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(rule("r-imposible", and(allNo, eq("M00", "1")), "M99")),
            facts(questions.toArray(QuestionFacts[]::new)));

    assertThat(warnings(report))
        .contains("NUNCA_SE_CUMPLE@regla:r-imposible")
        .doesNotContain("NO_ANALIZABLE@regla:r-imposible");
  }

  @Test
  void fiveHundredDisjointRulesAnalyzeQuickly() {
    Map<String, QuestionFacts> facts =
        facts(
            text("T01", 0),
            single("Q01", 1, "1", "2"),
            single("Q02", 2, "1", "2"),
            single("Q03", 3, "1", "2"),
            text("Z01", 9));
    List<SkipRule> skips = new ArrayList<>();
    for (int index = 0; index < 500; index++) {
      Condition allNo =
          new Condition(null, "eq", "2", null, List.of("Q01", "Q02", "Q03"), null, null, null);
      skips.add(rule(String.format("r-%03d", index), and(eq("T01", "a" + index), allNo), "Z01"));
    }
    RuleSet set = rules(skips.toArray(SkipRule[]::new));

    AnalysisReport report =
        assertTimeoutPreemptively(
            Duration.ofMillis(3000), () -> RuleSetAnalyzer.analyze(set, facts));

    assertThat(report.hasErrors()).isFalse();
  }

  @Test
  void threeThousandCheckboxRulesStayWithinMemory() {
    List<QuestionFacts> questions = new ArrayList<>();
    List<SkipRule> skips = new ArrayList<>();
    for (int index = 0; index < 3000; index++) {
      String column = String.format("K%04d", index);
      questions.add(checkbox(column, index));
      skips.add(rule(String.format("r-%04d", index), eq(column, "1"), "Z01"));
    }
    questions.add(text("Z01", 3000));
    Map<String, QuestionFacts> facts = facts(questions.toArray(QuestionFacts[]::new));
    RuleSet set = rules(skips.toArray(SkipRule[]::new));

    // Pasa el tope de reglas: la validación lo rechaza antes de enumerar.
    AnalysisReport report =
        assertTimeoutPreemptively(
            Duration.ofMillis(3000), () -> RuleSetAnalyzer.analyze(set, facts));
    // Aun sin el tope, de los ~4,5 millones de pares hacia Z01 solo se guardan los que el
    // presupuesto paga.
    LogicWarnings logic = new LogicWarnings(set, facts, Set.of());
    List<Finding> found =
        assertTimeoutPreemptively(Duration.ofMillis(3000), () -> logic.enumerated());

    assertThat(report.errors())
        .extracting(Finding::message)
        .containsExactly("Las reglas del año (archivo y heredadas) superan 1000.");
    assertThat(report.warnings()).isEmpty();
    assertThat(found)
        .extracting(f -> f.code() + "@" + f.element())
        .contains("NO_ANALIZABLE@archivo");
    assertThat(logic.plannedPairs()).isPositive();
    assertThat(logic.plannedPairs()).isLessThanOrEqualTo(RuleSetAnalyzer.MAX_TOTAL_WORK / 3000);
  }

  @Test
  void manyOverlappingPairsDoNotStarveCheaperRuleChecks() {
    List<QuestionFacts> questions = new ArrayList<>();
    List<SkipRule> skips = new ArrayList<>();
    for (int index = 0; index < 500; index++) {
      String column = String.format("K%04d", index);
      questions.add(checkbox(column, index));
      skips.add(rule(String.format("r-%04d", index), eq(column, "1"), "Z01"));
    }
    questions.add(text("Z01", 500));
    skips.add(rule("r-imposible", and(eq("K0000", "1"), eq("K0000", "0")), "Z01"));

    // Planear los ~125 000 pares no puede gastar el presupuesto de las reglas, más baratas.
    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(skips.toArray(SkipRule[]::new)), facts(questions.toArray(QuestionFacts[]::new)));

    assertThat(warnings(report)).contains("NUNCA_SE_CUMPLE@regla:r-imposible");
  }

  @Test
  void wideRulesStayBounded() {
    int ruleCount = 1000;
    for (boolean distinct : new boolean[] {true, false}) {
      List<QuestionFacts> questions = new ArrayList<>();
      List<String> common = new ArrayList<>();
      if (!distinct) {
        for (int index = 0; index < 13; index++) {
          common.add("S" + index);
          questions.add(checkbox("S" + index, questions.size()));
        }
      }
      List<SkipRule> skips = new ArrayList<>();
      for (int index = 0; index < ruleCount; index++) {
        List<String> columns = new ArrayList<>(common);
        for (int own = 0; own < (distinct ? 13 : 1); own++) {
          String column = String.format("K%04d_%02d", index, own);
          columns.add(column);
          questions.add(checkbox(column, questions.size()));
        }
        Condition anyOf = new Condition(null, "eq", "1", null, null, columns, null, null);
        skips.add(rule(String.format("r-%04d", index), anyOf, "Z01"));
      }
      questions.add(text("Z01", questions.size()));
      Map<String, QuestionFacts> facts = facts(questions.toArray(QuestionFacts[]::new));
      RuleSet set = rules(skips.toArray(SkipRule[]::new));

      AnalysisReport report =
          assertTimeoutPreemptively(
              Duration.ofMillis(3000), () -> RuleSetAnalyzer.analyze(set, facts));

      // Cada regla trae a lo sumo sus columnas y Z01 todas: lineal en el número de reglas.
      long namedColumns = report.warnings().stream().mapToLong(f -> f.questions().size()).sum();
      assertThat(namedColumns).as("distinct=%s", distinct).isLessThanOrEqualTo(30L * ruleCount);
      assertThat(warnings(report)).contains("NO_ANALIZABLE@regla:r-0000");
    }
  }

  @Test
  void chainWarningsStayLinear() {
    int ruleCount = 1000;
    List<QuestionFacts> questions = new ArrayList<>();
    List<SkipRule> skips = new ArrayList<>();
    for (int index = 0; index <= ruleCount; index++) {
      questions.add(checkbox(String.format("K%04d", index), index));
    }
    for (int index = 0; index < ruleCount; index++) {
      skips.add(
          rule(
              String.format("r-%04d", index),
              eq(String.format("K%04d", index), "1"),
              String.format("K%04d", index + 1)));
    }
    Map<String, QuestionFacts> facts = facts(questions.toArray(QuestionFacts[]::new));
    RuleSet set = rules(skips.toArray(SkipRule[]::new));

    AnalysisReport report =
        assertTimeoutPreemptively(
            Duration.ofMillis(3000), () -> RuleSetAnalyzer.analyze(set, facts));

    // Al final de la cadena cada aviso alcanza cientos de columnas: solo nombra las 20 primeras.
    long namedColumns = report.warnings().stream().mapToLong(f -> f.questions().size()).sum();
    assertThat(namedColumns).isLessThanOrEqualTo(20L * report.warnings().size());
    Finding last =
        report.warnings().stream()
            .filter(f -> f.element().equals("regla:r-0999"))
            .findFirst()
            .orElseThrow();
    assertThat(last.questions()).hasSize(20).startsWith("K0000", "K0001");
    assertThat(last.message()).contains("K0019 y 980 más");
  }

  @Test
  void unknownColumnsDoNotInflateTheAnalysis() {
    List<String> invented = new ArrayList<>();
    for (int index = 0; index < 13; index++) {
      invented.add(String.format("X%02d", index));
    }
    Condition anyInvented = new Condition(null, "eq", "1", null, null, invented, null, null);

    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(rule("r-inventada", and(eq("K01", "1"), anyInvented), "K02")),
            facts(checkbox("K01", 0), checkbox("K02", 1)));

    assertThat(report.errors()).extracting(Finding::code).contains("PREGUNTA_INEXISTENTE");
    assertThat(warnings(report)).doesNotContain("NO_ANALIZABLE@regla:r-inventada");
    assertThat(report.warnings())
        .allSatisfy(f -> assertThat(f.questions()).doesNotContainAnyElementsOf(invented));
  }

  @Test
  void realisticSizedQuestionnaireIsNotAllNoise() {
    List<QuestionFacts> questions = new ArrayList<>();
    for (int index = 0; index < 150; index++) {
      boolean isText = index % 10 == 9;
      List<String> options = index % 4 == 0 ? List.of("1", "2", "9") : List.of("1", "2");
      questions.add(
          new QuestionFacts(
              question(index),
              "P" + (index / 17),
              index,
              isText ? InputType.TEXT : InputType.SINGLE_CHOICE,
              isText ? List.of() : options,
              ColumnStatus.ACTIVE,
              null));
    }
    List<SkipRule> skips = new ArrayList<>();
    // Preguntas filtro cada 5: saltan su bloque según la respuesta, con una cascada de un nivel.
    for (int filter = 0; filter < 150; filter += 5) {
      String id = question(filter);
      skips.add(
          rule(
              id + "-no",
              eq(id, "2"),
              question(filter + 1),
              question(filter + 2),
              question(filter + 3),
              question(filter + 4)));
      skips.add(rule(id + "-si", eq(id, "1"), question(filter + 4)));
      skips.add(rule(id + "-vacia", leaf(id, "empty", null), question(filter + 1)));
      skips.add(rule(id + "-otra", leaf(id, "ne", "2"), question(filter + 2)));
      skips.add(rule(id + "-sub", eq(question(filter + 1), "1"), question(filter + 2)));
    }
    // Reglas especiales: todas de 7 preguntas = 2 deshabilitan una posterior.
    for (int special = 0; special < 12; special++) {
      int base = 5 + special * 11;
      List<String> all = new ArrayList<>();
      for (int step = 0; step < 7; step++) {
        int column = base + step;
        all.add(question(column % 10 == 9 ? column + 1 : column));
      }
      skips.add(
          rule(
              "especial-" + special,
              new Condition(null, "eq", "2", null, all, null, null, null),
              question(base + 8)));
    }
    RuleSet set = rules(skips.toArray(SkipRule[]::new));
    Map<String, QuestionFacts> facts = facts(questions.toArray(QuestionFacts[]::new));

    AnalysisReport report =
        assertTimeoutPreemptively(
            Duration.ofMillis(2000), () -> RuleSetAnalyzer.analyze(set, facts));

    long notAnalyzable =
        report.warnings().stream().filter(f -> f.code().equals("NO_ANALIZABLE")).count();
    assertThat(set.skips()).hasSizeGreaterThanOrEqualTo(160);
    assertThat(notAnalyzable).isLessThan(report.warnings().size() / 2);
    assertThat(warnings(report)).contains("REGLAS_SOLAPADAS@regla:Q000-otra");
    // Lo que no cabe en el presupuesto sale en un solo aviso de archivo, nunca uno por elemento.
    assertThat(report.warnings())
        .filteredOn(f -> f.code().equals("NO_ANALIZABLE") && !f.element().equals("archivo"))
        .allSatisfy(f -> assertThat(f.message()).contains("más de 4096 por elemento)"));
  }

  private static String question(int index) {
    return String.format("Q%03d", index);
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
