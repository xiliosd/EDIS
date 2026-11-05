package co.gov.dane.edi.admin.dto;

/**
 * Ayuda de una pregunta (FRM_PRE_AYUDA). {@code text} es la palabra o frase de la pregunta que se
 * vuelve enlace, la única parte editable; {@code codPreAyu} es su clave. {@code codAyu} apunta a la
 * definición en GRAL_AYUDAS, que se muestra solo como contexto en {@code definition} (nula si
 * GRAL_AYUDAS no tiene ese COD).
 */
public record AdminHelp(long codPreAyu, Long codAyu, String text, AdminHelpDefinition definition) {}
