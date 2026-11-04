package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.model.AnswerColumn;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.InputType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleSetAnalyzerErrorsTest {

  private static final AnswerColumn NUMBER_3 =
      new AnswerColumn("C01B", "NUMBER", 22, 0, false, 3, 0);
  private static final AnswerColumn VARCHAR_10_BYTES =
      new AnswerColumn("C02", "VARCHAR2", 10, 10, false, null, null);

  private static final Map<String, QuestionFacts> FACTS =
      Map.of(
          "C01A",
          new QuestionFacts(
              "C01A",
              "C1",
              0,
              InputType.SINGLE_CHOICE,
              List.of("1", "2"),
              ColumnStatus.ACTIVE,
              null),
          "C01B",
          new QuestionFacts(
              "C01B", "C1", 1, InputType.TEXT, List.of(), ColumnStatus.ACTIVE, NUMBER_3),
          "C02",
          new QuestionFacts(
              "C02", "C1", 2, InputType.TEXT, List.of(), ColumnStatus.ACTIVE, VARCHAR_10_BYTES),
          "C03",
          new QuestionFacts(
              "C03", "C1", 3, InputType.CHECKBOX, List.of("0", "1"), ColumnStatus.ACTIVE, null));

  private static Condition eq(String question, String value) {
    return new Condition(question, "eq", value, null, null, null, null, null);
  }

  private static SkipRule rule(String id, Condition when, String... targets) {
    return new SkipRule(id, id, when, new SkipAction("deshabilitar", List.of(targets)));
  }

  private static RuleSet rules(List<SkipRule> skips, Map<String, FieldValidation> validations) {
    return RuleSet.empty("EDIN", 2026).withVersion(1).withSkips(skips).withValidations(validations);
  }

  private static FieldValidation validation(
      Boolean required, String type, String min, String max, Integer maxLength) {
    return new FieldValidation(
        required,
        type,
        min == null ? null : new BigDecimal(min),
        max == null ? null : new BigDecimal(max),
        maxLength);
  }

  /** Los códigos de análisis; ESTRUCTURA (que repite lo que RuleSetValidator ya reporta) aparte. */
  private static List<String> codes(AnalysisReport report) {
    return report.errors().stream()
        .filter(f -> !f.code().equals(AnalysisCodes.ESTRUCTURA))
        .map(f -> f.code() + "@" + f.element())
        .toList();
  }

  @Test
  void unknownQuestionInConditionTargetValidationAndCase() {
    RuleSet set =
        rules(
                List.of(rule("r1", eq("X01", "1"), "C01B"), rule("r2", eq("C01A", "1"), "X02")),
                Map.of("X03", validation(true, null, null, null, null)))
            .withCases(
                List.of(
                    new TestCase(
                        "caso-1",
                        "Caso",
                        "C1",
                        Map.of("X04", "1"),
                        new ExpectedOutcome(List.of(), null),
                        null)));

    AnalysisReport report = RuleSetAnalyzer.analyze(set, FACTS);

    assertThat(codes(report))
        .containsExactly(
            "PREGUNTA_INEXISTENTE@caso:caso-1",
            "PREGUNTA_INEXISTENTE@regla:r1",
            "PREGUNTA_INEXISTENTE@regla:r2",
            "PREGUNTA_INEXISTENTE@validacion:X03");
    assertThat(report.errors()).anySatisfy(f -> assertThat(f.questions()).containsExactly("X01"));
  }

  @Test
  void directAndIndirectCycles() {
    RuleSet set =
        rules(
            List.of(rule("r1", eq("C01A", "1"), "C01B"), rule("r2", eq("C01B", "1"), "C01A")),
            Map.of());

    AnalysisReport report = RuleSetAnalyzer.analyze(set, FACTS);

    assertThat(codes(report)).containsExactly("CICLO@regla:r1", "CICLO@regla:r2");
    assertThat(report.errors())
        .allSatisfy(f -> assertThat(f.questions()).containsExactly("C01A", "C01B"));
  }

  @Test
  void valueNotAnOption() {
    AnalysisReport bad =
        RuleSetAnalyzer.analyze(
            rules(List.of(rule("r1", eq("C01A", "3"), "C01B")), Map.of()), FACTS);
    AnalysisReport good =
        RuleSetAnalyzer.analyze(
            rules(List.of(rule("r1", eq("C01A", "2"), "C01B")), Map.of()), FACTS);

    assertThat(codes(bad)).containsExactly("VALOR_NO_ES_OPCION@regla:r1");
    assertThat(bad.errors().get(0).message()).contains("C01A").contains("3");
    assertThat(good.errors()).isEmpty();
  }

  @Test
  void minGreaterThanMax() {
    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(List.of(), Map.of("C01B", validation(null, "entero", "10", "5", null))), FACTS);

    assertThat(codes(report)).containsExactly("RANGO_INVALIDO@validacion:C01B");
  }

  @Test
  void invalidMaxLengthAndNonIntegerBounds() {
    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(
                List.of(),
                Map.of(
                    "C02", validation(null, "texto", null, null, 0),
                    "C01B", validation(null, "entero", "1.5", null, null))),
            FACTS);

    // C01B (NUMBER(3,0)) tampoco admite el decimal: ademas de RANGO_INVALIDO excede la columna.
    assertThat(codes(report))
        .containsExactly(
            "RANGO_EXCEDE_COLUMNA@validacion:C01B",
            "RANGO_INVALIDO@validacion:C01B",
            "RANGO_INVALIDO@validacion:C02");
  }

  @Test
  void maxExceedsNumberPrecision() {
    AnalysisReport over =
        RuleSetAnalyzer.analyze(
            rules(List.of(), Map.of("C01B", validation(null, "entero", null, "1000", null))),
            FACTS);
    AnalysisReport fits =
        RuleSetAnalyzer.analyze(
            rules(List.of(), Map.of("C01B", validation(null, "entero", null, "999", null))), FACTS);

    assertThat(codes(over)).containsExactly("RANGO_EXCEDE_COLUMNA@validacion:C01B");
    assertThat(fits.errors()).isEmpty();
  }

  @Test
  void maxLengthExceedsBytes() {
    AnalysisReport over =
        RuleSetAnalyzer.analyze(
            rules(List.of(), Map.of("C02", validation(null, "texto", null, null, 11))), FACTS);
    AnalysisReport fits =
        RuleSetAnalyzer.analyze(
            rules(List.of(), Map.of("C02", validation(null, "texto", null, null, 10))), FACTS);

    assertThat(codes(over)).containsExactly("RANGO_EXCEDE_COLUMNA@validacion:C02");
    assertThat(fits.errors()).isEmpty();
  }

  @Test
  void rangeOnVarcharColumnIsAllowed() {
    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(List.of(), Map.of("C02", validation(null, "decimal", "0", "99999999999", null))),
            FACTS);

    assertThat(report.errors()).isEmpty();
  }

  @Test
  void newColumnProvidesCapacityWhenColumnDoesNotExistYet() {
    Map<String, QuestionFacts> facts =
        Map.of(
            "N01",
            new QuestionFacts(
                "N01", "C1", 0, InputType.TEXT, List.of(), ColumnStatus.PENDING_COLUMN, null));
    RuleSet numeric =
        rules(List.of(), Map.of("N01", validation(null, "entero", null, "100", null)))
            .withNewColumns(Map.of("N01", new NewColumn("NUMBER", 2)));
    RuleSet text =
        rules(List.of(), Map.of("N01", validation(null, "texto", null, null, 6)))
            .withNewColumns(Map.of("N01", new NewColumn("VARCHAR2", 5)));
    RuleSet unknownCapacity =
        rules(List.of(), Map.of("N01", validation(null, "entero", null, "100", 99)));

    assertThat(codes(RuleSetAnalyzer.analyze(numeric, facts)))
        .containsExactly("RANGO_EXCEDE_COLUMNA@validacion:N01");
    assertThat(codes(RuleSetAnalyzer.analyze(text, facts)))
        .containsExactly("RANGO_EXCEDE_COLUMNA@validacion:N01");
    assertThat(RuleSetAnalyzer.analyze(unknownCapacity, facts).errors()).isEmpty();
  }

  @Test
  void rangeOnCombo() {
    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(
                List.of(),
                Map.of(
                    "C01A", validation(null, null, "1", null, null),
                    "C03", validation(null, "entero", null, null, null))),
            FACTS);

    assertThat(codes(report))
        .containsExactly(
            "VALIDACION_EN_OPCION@validacion:C01A", "VALIDACION_EN_OPCION@validacion:C03");
  }

  @Test
  void requiredOnlyOnComboIsFine() {
    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(
                List.of(),
                Map.of(
                    "C01A", validation(false, null, null, null, null),
                    "C03", validation(true, "opcion", null, null, null))),
            FACTS);

    assertThat(report.errors()).isEmpty();
  }

  @Test
  void structureProblemsBecomeFindings() {
    SkipRule broken =
        new SkipRule("r1", "sin condicion", null, new SkipAction("deshabilitar", List.of("C01B")));

    AnalysisReport report = RuleSetAnalyzer.analyze(rules(List.of(broken), Map.of()), FACTS);

    assertThat(report.errors()).hasSize(1);
    assertThat(report.errors().get(0).code()).isEqualTo("ESTRUCTURA");
    assertThat(report.errors().get(0).element()).isEqualTo("archivo");
    assertThat(report.errors().get(0).message()).contains("r1");
  }

  @Test
  void newErrorsComparedToIgnoresPreexisting() {
    Finding existing = new Finding("PREGUNTA_INEXISTENTE", "regla:r1", "uno", List.of("X01"));
    Finding sameDifferentMessage =
        new Finding("PREGUNTA_INEXISTENTE", "regla:r1", "otro texto", List.of("X01"));
    Finding added = new Finding("CICLO", "regla:r2", "ciclo", List.of("C01A", "C01B"));
    AnalysisReport previous = new AnalysisReport(List.of(existing), List.of());
    AnalysisReport candidate = new AnalysisReport(List.of(sameDifferentMessage, added), List.of());

    assertThat(candidate.newErrorsComparedTo(previous)).containsExactly(added);
    assertThat(candidate.hasErrors()).isTrue();
    assertThat(new AnalysisReport(List.of(), List.of()).hasErrors()).isFalse();
  }

  private static Condition nested(String op, Condition... children) {
    List<Condition> list = List.of(children);
    return "y".equals(op)
        ? new Condition(null, null, null, null, null, null, list, null)
        : new Condition(null, null, null, null, null, null, null, list);
  }

  @Test
  void nestedConditionsAreAnalyzed() {
    Condition invalid = nested("y", eq("C02", "x"), nested("o", eq("C01A", "3"), eq("C03", "1")));
    Condition valid = nested("y", eq("C02", "x"), nested("o", eq("C01A", "2"), eq("C03", "1")));

    AnalysisReport bad =
        RuleSetAnalyzer.analyze(rules(List.of(rule("r1", invalid, "C01B")), Map.of()), FACTS);
    AnalysisReport good =
        RuleSetAnalyzer.analyze(rules(List.of(rule("r1", valid, "C01B")), Map.of()), FACTS);

    assertThat(codes(bad)).containsExactly("VALOR_NO_ES_OPCION@regla:r1");
    assertThat(good.errors()).isEmpty();
  }

  @Test
  void nestedLeavesAreCycleTriggers() {
    RuleSet set =
        rules(
            List.of(
                rule("r1", nested("y", eq("C01A", "1"), eq("C03", "1")), "C01B"),
                rule("r2", eq("C01B", "x"), "C03")),
            Map.of());

    assertThat(codes(RuleSetAnalyzer.analyze(set, FACTS)))
        .containsExactly("CICLO@regla:r1", "CICLO@regla:r2");
  }

  @Test
  void partialFixCreatesNoNewErrors() {
    AnalysisReport before =
        RuleSetAnalyzer.analyze(
            rules(
                List.of(rule("r1", nested("o", eq("X01", "1"), eq("X02", "1")), "C01B")), Map.of()),
            FACTS);
    AnalysisReport after =
        RuleSetAnalyzer.analyze(
            rules(List.of(rule("r1", eq("X02", "1"), "C01B")), Map.of()), FACTS);

    assertThat(before.errors()).hasSize(2);
    assertThat(after.newErrorsComparedTo(before)).isEmpty();
  }

  @Test
  void partialFixOfOptionValuesCreatesNoNewErrors() {
    Condition both = nested("o", eq("C01A", "8"), eq("C03", "9"));
    AnalysisReport before =
        RuleSetAnalyzer.analyze(rules(List.of(rule("r1", both, "C01B")), Map.of()), FACTS);
    AnalysisReport after =
        RuleSetAnalyzer.analyze(
            rules(List.of(rule("r1", eq("C03", "9"), "C01B")), Map.of()), FACTS);

    assertThat(before.errors()).hasSize(2);
    assertThat(after.newErrorsComparedTo(before)).isEmpty();
  }

  @Test
  void cycleFindingsStableWhenOneCycleRemoved() {
    SkipRule ab = rule("r1", eq("C01A", "1"), "C01B");
    SkipRule bc = rule("r2", eq("C01B", "x"), "C02");
    SkipRule ca = rule("r3", eq("C02", "x"), "C01A");
    SkipRule ba = rule("r4", eq("C01B", "x"), "C01A");
    AnalysisReport before =
        RuleSetAnalyzer.analyze(rules(List.of(ab, bc, ca, ba), Map.of()), FACTS);
    AnalysisReport after = RuleSetAnalyzer.analyze(rules(List.of(ab, bc, ca), Map.of()), FACTS);

    assertThat(codes(before))
        .containsExactly("CICLO@regla:r1", "CICLO@regla:r2", "CICLO@regla:r3", "CICLO@regla:r4");
    assertThat(codes(after)).hasSize(3);
    assertThat(after.newErrorsComparedTo(before)).isEmpty();
  }

  @Test
  void selfCycle() {
    AnalysisReport report =
        RuleSetAnalyzer.analyze(rules(List.of(rule("r1", eq("C02", "x"), "C02")), Map.of()), FACTS);

    assertThat(codes(report)).containsExactly("CICLO@regla:r1");
    assertThat(report.errors())
        .anySatisfy(
            f -> {
              assertThat(f.code()).isEqualTo("CICLO");
              assertThat(f.questions()).containsExactly("C02");
            });
  }

  @Test
  void threeNodeCycle() {
    AnalysisReport report =
        RuleSetAnalyzer.analyze(
            rules(
                List.of(
                    rule("r1", eq("C01A", "1"), "C01B"),
                    rule("r2", eq("C01B", "x"), "C02"),
                    rule("r3", eq("C02", "x"), "C01A")),
                Map.of()),
            FACTS);

    assertThat(codes(report)).containsExactly("CICLO@regla:r1", "CICLO@regla:r2", "CICLO@regla:r3");
    assertThat(report.errors())
        .allSatisfy(f -> assertThat(f.questions()).containsExactly("C01A", "C01B", "C02"));
  }
}
