package co.gov.dane.edi.operation.novelty.dto;

/** Entidad de un departamento con su muestra activa y cuántos funcionarios tienen novedad. */
public record NoveltyEntity(long code, String name, long sampleSize, long withNovelty) {}
