package co.gov.dane.edi.rules;

import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.EntryKind;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.model.QuestionnaireEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * Traduce el salto genérico de FRM_SALTO del PHP ({@code SALTOS()} con VALOR_SALTO = 2): si el
 * combo vale 2 se deshabilitan los SALTO combos siguientes de la página. VALOR_SALTO = 1 marcaba
 * reglas especiales escritas a mano en FrmValida.js; esas viven en la semilla de reglas (Tarea 17).
 */
public final class LegacySkipTranslator {

  private static final String GENERIC_TRIGGER = "2";

  private LegacySkipTranslator() {}

  public static List<SkipRule> translate(List<QuestionnaireEntry> pageEntries) {
    List<String> combos =
        pageEntries.stream()
            .filter(LegacySkipTranslator::isActiveCombo)
            .map(entry -> entry.answer().columnCode())
            .toList();
    List<SkipRule> rules = new ArrayList<>();
    for (QuestionnaireEntry entry : pageEntries) {
      if (!isActiveCombo(entry)
          || entry.legacySkip() == null
          || !GENERIC_TRIGGER.equals(entry.legacySkip().triggerValue())) {
        continue;
      }
      String trigger = entry.answer().columnCode();
      int from = combos.indexOf(trigger) + 1;
      List<String> targets =
          combos.subList(from, Math.min(combos.size(), from + entry.legacySkip().count()));
      if (!targets.isEmpty()) {
        rules.add(
            new SkipRule(
                "legacy-" + trigger,
                "Salto heredado de FRM_SALTO",
                new Condition(trigger, "eq", GENERIC_TRIGGER, null, null, null, null, null),
                new SkipAction("deshabilitar", List.copyOf(targets))));
      }
    }
    return rules;
  }

  private static boolean isActiveCombo(QuestionnaireEntry entry) {
    return entry.kind() == EntryKind.ITEM
        && entry.answer() != null
        && entry.answer().inputType() == InputType.SINGLE_CHOICE
        && entry.answer().columnStatus() == ColumnStatus.ACTIVE;
  }
}
