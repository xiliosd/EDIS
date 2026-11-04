package co.gov.dane.edi.admin.dto;

/**
 * Cuántos encuestados ya guardaron una página que la importación cambia (salto o validación). Es
 * solo un conteo: nunca lleva códigos de encuestado ni respuestas.
 */
public record PageImpact(String pageCode, long savedCount) {}
