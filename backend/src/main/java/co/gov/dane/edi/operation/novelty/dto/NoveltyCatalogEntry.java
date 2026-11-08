package co.gov.dane.edi.operation.novelty.dto;

/**
 * Novedad del catálogo GRAL_NOVEDAD de la encuesta y el año; {@code requiresText} marca la de "otra
 * causa", que exige escribir la causa.
 */
public record NoveltyCatalogEntry(int code, String name, boolean requiresText) {}
