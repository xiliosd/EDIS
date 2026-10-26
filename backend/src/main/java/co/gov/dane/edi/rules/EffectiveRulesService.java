package co.gov.dane.edi.rules;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.service.QuestionnaireService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Reglas que aplican de verdad: las del archivo más los saltos heredados de FRM_SALTO. */
@Service
public class EffectiveRulesService {

  private final RulesFileRepository rulesFile;
  private final QuestionnaireService questionnaire;
  private final SurveyProperties survey;

  /** Una sola instancia (D6): las reglas efectivas se calculan una vez por generación. */
  private final AtomicReference<CachedRules> cached = new AtomicReference<>();

  public EffectiveRulesService(
      RulesFileRepository rulesFile, QuestionnaireService questionnaire, SurveyProperties survey) {
    this.rulesFile = rulesFile;
    this.questionnaire = questionnaire;
    this.survey = survey;
  }

  public RuleSet effectiveRules() {
    long generation = questionnaire.cacheGeneration();
    CachedRules current = cached.get();
    if (current != null && current.generation() == generation) {
      return current.rules();
    }
    RuleSet computed = compute();
    cached.set(new CachedRules(generation, computed));
    return computed;
  }

  private RuleSet compute() {
    List<SkipRule> legacy = new ArrayList<>();
    for (SurveyPage page : SurveyPage.ordered()) {
      legacy.addAll(LegacySkipTranslator.translate(questionnaire.pageEntries(page)));
    }
    return merge(rulesFile.load(survey.code(), survey.year()), legacy);
  }

  private record CachedRules(long generation, RuleSet rules) {}

  /** Una regla del archivo con el id de una heredada la reemplaza (así se corrige FRM_SALTO). */
  static RuleSet merge(RuleSet fromFile, List<SkipRule> legacy) {
    Set<String> fileIds = fromFile.skips().stream().map(SkipRule::id).collect(Collectors.toSet());
    List<SkipRule> skips = new ArrayList<>(fromFile.skips());
    legacy.stream().filter(rule -> !fileIds.contains(rule.id())).forEach(skips::add);
    return new RuleSet(
        fromFile.survey(),
        fromFile.year(),
        fromFile.version(),
        skips,
        fromFile.validations(),
        fromFile.state(),
        fromFile.tables());
  }
}
