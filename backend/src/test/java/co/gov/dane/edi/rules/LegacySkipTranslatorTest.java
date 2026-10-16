package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.model.AnswerSpec;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.EntryKind;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.model.LegacySkip;
import co.gov.dane.edi.model.QuestionnaireEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacySkipTranslatorTest {

  private static QuestionnaireEntry combo(String column, LegacySkip skip) {
    return new QuestionnaireEntry(
        1,
        "C1",
        EntryKind.ITEM,
        column,
        column,
        "t",
        List.of(),
        new AnswerSpec(InputType.SINGLE_CHOICE, column, ColumnStatus.ACTIVE, List.of()),
        skip);
  }

  private static QuestionnaireEntry text(String column) {
    return new QuestionnaireEntry(
        1,
        "C1",
        EntryKind.ITEM,
        column,
        column,
        "t",
        List.of(),
        new AnswerSpec(InputType.TEXT, column, ColumnStatus.ACTIVE, List.of()),
        null);
  }

  private static QuestionnaireEntry statement() {
    return new QuestionnaireEntry(
        1, "C1", EntryKind.STATEMENT, "C1.", "C1.", "t", List.of(), null, null);
  }

  @Test
  void genericSkipDisablesTheNextCombosOnly() {
    List<SkipRule> rules =
        LegacySkipTranslator.translate(
            List.of(
                combo("C01A", new LegacySkip(2, "2")),
                statement(),
                text("C01T"),
                combo("C01B", null),
                combo("C01C", null),
                combo("C01D", null)));

    assertThat(rules).hasSize(1);
    SkipRule rule = rules.get(0);
    assertThat(rule.id()).isEqualTo("legacy-C01A");
    assertThat(rule.when().question()).isEqualTo("C01A");
    assertThat(rule.when().op()).isEqualTo("eq");
    assertThat(rule.when().value()).isEqualTo("2");
    assertThat(rule.then().questions()).containsExactly("C01B", "C01C");
  }

  @Test
  void specialSkipsMarkedWithValueOneAreLeftToTheSeedRules() {
    assertThat(
            LegacySkipTranslator.translate(
                List.of(combo("C07A", new LegacySkip(6, "1")), combo("C08A", null))))
        .isEmpty();
  }

  @Test
  void skipAtTheEndOfThePageProducesNoRule() {
    assertThat(LegacySkipTranslator.translate(List.of(combo("H01", new LegacySkip(3, "2")))))
        .isEmpty();
  }
}
