package co.gov.dane.edi.admin.dto;

/**
 * Lo que la administración necesita saber del ambiente. {@code questionnaireWritesEnabled} en false
 * significa solo lectura: el front deshabilita los controles que cambian el cuestionario.
 */
public record AdminEnvironment(boolean questionnaireWritesEnabled) {}
