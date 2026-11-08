package co.gov.dane.edi.operation.report.dto;

import co.gov.dane.edi.operation.report.ReportSurvey;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Pide un corte de un departamento; sin {@code anio} se usa el de la encuesta activa. */
public record CutRequest(
    @NotNull ReportSurvey encuesta,
    @Min(2020) @Max(2100) Integer anio,
    @NotNull @Min(1) @Max(999) Integer departamento) {}
