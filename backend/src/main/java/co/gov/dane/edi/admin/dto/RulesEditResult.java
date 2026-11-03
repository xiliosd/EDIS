package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.rules.AnalysisReport;
import co.gov.dane.edi.rules.CaseResult;
import java.util.List;

/** Resultado de guardar reglas: la versión nueva, el análisis completo y el estado de los casos. */
public record RulesEditResult(int version, AnalysisReport report, List<CaseResult> caseResults) {}
