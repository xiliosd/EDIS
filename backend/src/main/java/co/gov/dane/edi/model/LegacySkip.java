package co.gov.dane.edi.model;

/**
 * Salto simple de FRM_SALTO: con la respuesta triggerValue se deshabilitan las count siguientes.
 */
public record LegacySkip(int count, String triggerValue) {}
