package co.gov.dane.edi.model;

/** Metadatos de ALL_TAB_COLUMNS: permiten rechazar con 422 un valor que no cabe en la columna. */
public record AnswerColumn(String name, String dataType, int dataLength) {}
