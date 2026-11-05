package co.gov.dane.edi.admin.dto;

/**
 * Cuerpo opcional de los DELETE de reglas. {@code motivo} viaja en el cuerpo y no en la URL para
 * que nunca llegue a los registros de acceso de un proxy; solo lo exige una corrección sobre un año
 * publicado (10 a 500 caracteres).
 */
public record CorrectionReasonRequest(String motivo) {}
