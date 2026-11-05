package co.gov.dane.edi.admin.dto;

/**
 * Definición de una ayuda en GRAL_AYUDAS, solo lectura y en texto plano: {@code title} es TITULO y
 * {@code excerpt} el comienzo de DESCRIPCION, sin HTML. Editar las definiciones es de la Fase 4.
 */
public record AdminHelpDefinition(String title, String excerpt) {}
