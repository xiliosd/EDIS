package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.gov.dane.edi.admin.dto.RulesEditResult;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.rules.AnalysisCodes;
import co.gov.dane.edi.rules.Condition;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.Finding;
import co.gov.dane.edi.rules.QuestionFacts;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RuleSetAnalyzer;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.SkipAction;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.SaveLocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El tope de reglas se cuenta sobre las reglas efectivas del año: archivo más heredadas. */
class RulesAdminServiceTest {

  private static final String SURVEY = "EDIN";
  private static final int YEAR = 2027;

  private final YearCatalogService years = mock(YearCatalogService.class);
  private final RulesFileRepository rulesFiles = mock(RulesFileRepository.class);
  private final EffectiveRulesService effectiveRules = mock(EffectiveRulesService.class);
  private final QuestionFactsService factsService = mock(QuestionFactsService.class);
  private RulesAdminService service;

  @BeforeEach
  void createService() {
    service =
        new RulesAdminService(
            years,
            rulesFiles,
            effectiveRules,
            factsService,
            mock(QuestionnaireService.class),
            new SaveLocks(),
            null);
    Map<String, QuestionFacts> facts = new LinkedHashMap<>();
    for (String column : List.of("A01", "B01", "Z01")) {
      facts.put(
          column,
          new QuestionFacts(
              column,
              "C1",
              facts.size(),
              InputType.CHECKBOX,
              List.of("0", "1"),
              ColumnStatus.ACTIVE,
              null));
    }
    when(factsService.facts(SURVEY, YEAR)).thenReturn(facts);
    when(rulesFiles.save(any(), anyInt(), anyString(), anyString(), anyList(), any()))
        .thenAnswer(invocation -> invocation.<RuleSet>getArgument(0).withVersion(2));
  }

  private static SkipRule rule(String id, String trigger, String target) {
    return new SkipRule(
        id,
        "",
        new Condition(trigger, "eq", "1", null, null, null, null, null),
        new SkipAction("deshabilitar", List.of(target)));
  }

  private static List<SkipRule> rules(String prefix, int count) {
    List<SkipRule> rules = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      rules.add(rule(prefix + "-" + index, "A01", "Z01"));
    }
    return rules;
  }

  private RuleSet givenYear(List<SkipRule> fileSkips, List<SkipRule> legacy) {
    RuleSet current = RuleSet.empty(SURVEY, YEAR).withVersion(1).withSkips(fileSkips);
    when(years.requireYear(SURVEY, YEAR)).thenReturn(current);
    when(effectiveRules.legacySkips(SURVEY, YEAR)).thenReturn(legacy);
    return current;
  }

  @Test
  void effectiveRulesBeyondLimitAreRejectedBeforeAnalysis() {
    // 845 del archivo y 160 heredadas: cada parte cabe, el año no.
    RuleSet current = givenYear(rules("f", 845), rules("legacy", 160));
    List<SkipRule> candidate = new ArrayList<>(current.skips());
    candidate.add(rule("ciclo-a", "A01", "B01"));
    candidate.add(rule("ciclo-b", "B01", "A01"));
    candidate.add(rule("inexistente", "NOEXISTE", "Z01"));

    InvalidRulesException rejected =
        catchThrowableOfType(
            InvalidRulesException.class,
            () ->
                service.importRules(SURVEY, YEAR, current.withSkips(candidate), 1, "admin", null));

    assertThat(rejected.report().errors())
        .extracting(Finding::code, Finding::message)
        .containsExactly(tuple(AnalysisCodes.ESTRUCTURA, RuleSetAnalyzer.OVERSIZED_YEAR_MESSAGE));
    assertThat(RuleSetAnalyzer.OVERSIZED_YEAR_MESSAGE)
        .isEqualTo("Las reglas del año (archivo y heredadas) superan 1000.");
    verify(factsService, never()).facts(SURVEY, YEAR);
    verify(rulesFiles, never()).save(any(), anyInt(), anyString(), anyString(), anyList(), any());
  }

  @Test
  void saveThatBringsAnOversizedYearUnderTheLimitIsAccepted() {
    // El año vigente pasa el tope por las heredadas, y una de ellas nombra una columna que no
    // existe: ese error ya estaba, así que no puede bloquear el guardado que achica el año.
    List<SkipRule> legacy = rules("legacy", 159);
    legacy.add(rule("legacy-inexistente", "NOEXISTE", "Z01"));
    RuleSet current = givenYear(rules("f", 845), legacy);

    RulesEditResult result =
        service.importRules(SURVEY, YEAR, current.withSkips(rules("f", 800)), 1, "admin", null);

    assertThat(result.version()).isEqualTo(2);
    assertThat(result.report().errors())
        .extracting(Finding::code)
        .contains(AnalysisCodes.PREGUNTA_INEXISTENTE);
    verify(rulesFiles).save(any(), anyInt(), anyString(), anyString(), anyList(), any());
  }
}
