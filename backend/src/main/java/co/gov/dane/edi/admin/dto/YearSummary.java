package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.admin.YearState;

/** Un año del cuestionario; {@code active} indica el año que atiende hoy el encuestado. */
public record YearSummary(int year, YearState state, int version, boolean active) {}
