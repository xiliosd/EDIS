package co.gov.dane.edi.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Año nuevo del cuestionario y el año del que se copia. */
public record CreateYearRequest(
    @NotNull(message = "Indique el año nuevo.") Integer year,
    @NotNull(message = "Indique el año de origen.") Integer fromYear) {}
