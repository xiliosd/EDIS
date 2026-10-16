package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EffectiveRulesMergeTest {

  private static SkipRule rule(String id, String trigger, String target) {
    return new SkipRule(
        id,
        null,
        new Condition(trigger, "eq", "2", null, null, null, null, null),
        new SkipAction("deshabilitar", List.of(target)));
  }

  @Test
  void fileRuleReplacesTheLegacyRuleWithTheSameId() {
    RuleSet fromFile =
        new RuleSet(
            "EDIN",
            2026,
            3,
            List.of(rule("legacy-C09G", "C09A", "C10"), rule("propia", "B10", "B11")),
            Map.of());
    List<SkipRule> legacy =
        List.of(rule("legacy-C09G", "C09G", "C10"), rule("legacy-E05", "E05", "E06A"));

    RuleSet merged = EffectiveRulesService.merge(fromFile, legacy);

    assertThat(merged.skips())
        .extracting(SkipRule::id)
        .containsExactly("legacy-C09G", "propia", "legacy-E05");
    assertThat(merged.skips().get(0).when().question()).isEqualTo("C09A");
    assertThat(merged.version()).isEqualTo(3);
  }
}
