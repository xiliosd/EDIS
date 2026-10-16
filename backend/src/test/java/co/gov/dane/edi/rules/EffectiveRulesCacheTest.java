package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.service.QuestionnaireService;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectiveRulesCacheTest {

  @Test
  void effectiveRulesAreCachedUntilTheQuestionnaireIsEvicted() {
    RulesFileRepository rulesFile = mock(RulesFileRepository.class);
    QuestionnaireService questionnaire = mock(QuestionnaireService.class);
    when(rulesFile.load()).thenReturn(RuleSet.empty("EDIN", 2026));
    when(questionnaire.pageEntries(any(SurveyPage.class))).thenReturn(List.of());
    when(questionnaire.cacheGeneration()).thenReturn(0L, 0L, 1L);
    EffectiveRulesService service = new EffectiveRulesService(rulesFile, questionnaire);

    RuleSet first = service.effectiveRules();
    assertThat(service.effectiveRules()).isSameAs(first);
    verify(rulesFile, times(1)).load();

    assertThat(service.effectiveRules()).isNotSameAs(first);
    verify(rulesFile, times(2)).load();
  }
}
