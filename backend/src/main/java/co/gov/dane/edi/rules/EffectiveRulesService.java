package co.gov.dane.edi.rules;

import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.service.QuestionnaireService;
import java.util.ArrayList;
import java.util.List;
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
    RuleSet fromFile = rulesFile.load();
    List<SkipRule> skips = new ArrayList<>(fromFile.skips());
    for (SurveyPage page : SurveyPage.ordered()) {
      skips.addAll(LegacySkipTranslator.translate(questionnaire.pageEntries(page)));
    }
    return new RuleSet(
        fromFile.survey(), fromFile.year(), fromFile.version(), skips, fromFile.validations());
  }
}
