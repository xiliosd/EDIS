package co.gov.dane.edi.rules;

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
    return new RuleSet("EDIN", 2026, 1, List.of(rule), Map.of());
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
    RuleSet duplicated = new RuleSet("EDIN", 2026, 1, List.of(first, first), Map.of());
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
                    null)));
    assertThatThrownBy(() -> RuleSetValidator.validate(rules)).hasMessageContaining("B06");
  }
}
