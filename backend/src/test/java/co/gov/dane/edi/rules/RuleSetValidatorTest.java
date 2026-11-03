package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleSetValidatorTest {

  private static Condition eq(String question, String value) {
    return new Condition(question, "eq", value, null, null, null, null, null);
  }

  private static RuleSet withSkip(SkipRule rule) {
    return new RuleSet("EDIN", 2026, 1, List.of(rule), Map.of(), null, Map.of());
  }

  @Test
  void acceptsAWellFormedRuleSet() {
    SkipRule rule =
        new SkipRule(
            "c01a-no",
            "Si C01A es No",
            eq("C01A", "2"),
            new SkipAction("deshabilitar", List.of("C01B")));
    assertThatCode(() -> RuleSetValidator.validate(withSkip(rule))).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnknownOperator() {
    SkipRule rule =
        new SkipRule(
            "x",
            null,
            new Condition("C01A", "contiene", "2", null, null, null, null, null),
            new SkipAction("deshabilitar", List.of("C01B")));
    assertThatThrownBy(() -> RuleSetValidator.validate(withSkip(rule)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("x")
        .hasMessageContaining("contiene");
  }

  @Test
  void rejectsUnsupportedActionAndEmptyTargets() {
    SkipRule badAction =
        new SkipRule("a", null, eq("C01A", "2"), new SkipAction("ocultar", List.of("C01B")));
    SkipRule noTargets =
        new SkipRule("b", null, eq("C01A", "2"), new SkipAction("deshabilitar", List.of()));
    assertThatThrownBy(() -> RuleSetValidator.validate(withSkip(badAction)))
        .hasMessageContaining("ocultar");
    assertThatThrownBy(() -> RuleSetValidator.validate(withSkip(noTargets)))
        .hasMessageContaining("b");
  }

  @Test
  void rejectsRuleThatDisablesItsOwnTrigger() {
    SkipRule selfDisabling =
        new SkipRule(
            "self", null, eq("C01A", "2"), new SkipAction("deshabilitar", List.of("C01A")));
    assertThatThrownBy(() -> RuleSetValidator.validate(withSkip(selfDisabling)))
        .hasMessageContaining("self");
  }

  @Test
  void rejectsDuplicateIdsAndInvalidColumnNames() {
    SkipRule first =
        new SkipRule("dup", null, eq("C01A", "2"), new SkipAction("deshabilitar", List.of("C01B")));
    RuleSet duplicated =
        new RuleSet("EDIN", 2026, 1, List.of(first, first), Map.of(), null, Map.of());
    SkipRule badColumn =
        new SkipRule(
            "col", null, eq("C01A; DROP", "2"), new SkipAction("deshabilitar", List.of("C01B")));
    assertThatThrownBy(() -> RuleSetValidator.validate(duplicated)).hasMessageContaining("dup");
    assertThatThrownBy(() -> RuleSetValidator.validate(withSkip(badColumn)))
        .hasMessageContaining("col");
  }

  @Test
  void rejectsValidationWithMinGreaterThanMax() {
    RuleSet rules =
        new RuleSet(
            "EDIN",
            2026,
            1,
            List.of(),
            Map.of(
                "B06",
                new FieldValidation(
                    true,
                    "entero",
                    new java.math.BigDecimal("99"),
                    new java.math.BigDecimal("15"),
                    null)),
            null,
            Map.of());
    assertThatThrownBy(() -> RuleSetValidator.validate(rules)).hasMessageContaining("B06");
  }

  @Test
  void validatorRejectsUnknownChapterOrBadTableName() {
    RuleSet base = RuleSet.empty("EDIN", 2027);
    assertThatThrownBy(() -> RuleSetValidator.validate(base.withTables(Map.of("Z", "TABLA_OK"))))
        .hasMessageContaining("capítulo");
    assertThatThrownBy(() -> RuleSetValidator.validate(base.withTables(Map.of("B", "X; DROP"))))
        .hasMessageContaining("tabla");
    assertThatThrownBy(() -> RuleSetValidator.validate(base.withTables(Map.of("B", "tabla_b"))))
        .hasMessageContaining("tabla");
    RuleSetValidator.validate(base.withTables(Map.of("B", "EDIN_21_CAP_B")));
  }

  private static TestCase testCase(String id, String name, String page) {
    return new TestCase(
        id, name, page, Map.of("C01A", "2"), new ExpectedOutcome(List.of("C01B"), Map.of()), null);
  }

  private static RuleSet withCases(TestCase... cases) {
    return RuleSet.empty("EDIN", 2026).withCases(List.of(cases));
  }

  @Test
  void caseProblems() {
    assertThat(RuleSetValidator.problems(withCases(testCase("c01a-no", "Caso válido", "C1"))))
        .isEmpty();
    assertThat(
            RuleSetValidator.problems(
                withCases(testCase("c01a-no", "Uno", "C1"), testCase("c01a-no", "Dos", "C1"))))
        .hasSize(1)
        .first()
        .asString()
        .contains("repetido");
    assertThat(RuleSetValidator.problems(withCases(testCase("C 1", "Nombre", "C1")))).hasSize(1);
    assertThat(RuleSetValidator.problems(withCases(testCase("c01a-no", "", "C1")))).hasSize(1);
    assertThat(RuleSetValidator.problems(withCases(testCase("c01a-no", "x".repeat(100), "C1"))))
        .isEmpty();
    assertThat(RuleSetValidator.problems(withCases(testCase("c01a-no", "x".repeat(101), "C1"))))
        .hasSize(1);
    assertThat(RuleSetValidator.problems(withCases(testCase("a".repeat(40), "Nombre", "C1"))))
        .isEmpty();
    assertThat(RuleSetValidator.problems(withCases(testCase("a".repeat(41), "Nombre", "C1"))))
        .hasSize(1);
    assertThat(RuleSetValidator.problems(withCases(testCase("c01a-no", "Nombre", "Z9"))))
        .hasSize(1);
  }

  @Test
  void problemsDoesNotThrow() {
    SkipRule withoutTargets =
        new SkipRule("x", null, eq("C01A", "2"), new SkipAction("deshabilitar", List.of()));
    RuleSet rules = withSkip(withoutTargets);

    assertThat(RuleSetValidator.problems(rules)).hasSize(1);
    assertThatThrownBy(() -> RuleSetValidator.validate(rules))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Reglas inválidas: ");
  }
}
