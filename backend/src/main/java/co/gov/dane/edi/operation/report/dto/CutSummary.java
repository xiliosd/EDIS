package co.gov.dane.edi.operation.report.dto;

import co.gov.dane.edi.operation.report.ReportSurvey;
import java.time.OffsetDateTime;

/** Datos de un corte para el histórico; {@code generatedAt} va en hora de Bogotá (-05:00). */
public record CutSummary(
    String id,
    ReportSurvey survey,
    int year,
    int departmentCode,
    String departmentName,
    String generatedBy,
    OffsetDateTime generatedAt) {}
