package co.gov.dane.edi.rules;

import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.service.QuestionnaireService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Reglas que aplican de verdad: las del archivo más los saltos heredados de FRM_SALTO. */
@Service
public class EffectiveRulesService {

  private final RulesFileRepository rulesFile;
  private final QuestionnaireService questionnaire;

  public EffectiveRulesService(RulesFileRepository rulesFile, QuestionnaireService questionnaire) {
    this.rulesFile = rulesFile;
    this.questionnaire = questionnaire;
  }

  public RuleSet effectiveRules() {
    List<SkipRule> legacy = new ArrayList<>();
    for (SurveyPage page : SurveyPage.ordered()) {
      legacy.addAll(LegacySkipTranslator.translate(questionnaire.pageEntries(page)));
    }
    return merge(rulesFile.load(), legacy);
  }

  /** Una regla del archivo con el id de una heredada la reemplaza (así se corrige FRM_SALTO). */
  static RuleSet merge(RuleSet fromFile, List<SkipRule> legacy) {
    Set<String> fileIds = fromFile.skips().stream().map(SkipRule::id).collect(Collectors.toSet());
    List<SkipRule> skips = new ArrayList<>(fromFile.skips());
    legacy.stream().filter(rule -> !fileIds.contains(rule.id())).forEach(skips::add);
    return new RuleSet(
        fromFile.survey(), fromFile.year(), fromFile.version(), skips, fromFile.validations());
  }
}
