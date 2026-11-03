package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.admin.YearState;
import co.gov.dane.edi.rules.AnalysisReport;
import co.gov.dane.edi.rules.CaseResult;
import co.gov.dane.edi.rules.FieldValidation;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.rules.TestCase;
import java.util.List;
import java.util.Map;

/**
 * Reglas de un año tal como las ve el editor. {@code skips} son las reglas efectivas (archivo más
 * heredadas); {@code legacySkipIds} son las que no están en el archivo y solo se pueden leer.
 */
public record RulesEditorView(
    String survey,
    int year,
    YearState state,
    int version,
    List<SkipRule> skips,
    List<String> legacySkipIds,
    Map<String, FieldValidation> validations,
    List<TestCase> cases,
    AnalysisReport report,
    List<CaseResult> caseResults,
    List<RuleQuestion> questions) {}
