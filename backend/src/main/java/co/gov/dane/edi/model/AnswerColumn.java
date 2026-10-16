package co.gov.dane.edi.model;

/**
 * Metadatos de ALL_TAB_COLUMNS: permiten rechazar con 422 un valor que Oracle no aceptaría
 * (ORA-12899, ORA-01438) en lugar de responder 503 o redondearlo en silencio.
 *
 * @param dataLength longitud en bytes (DATA_LENGTH)
 * @param charLength longitud en caracteres (CHAR_LENGTH)
 * @param charSemantics {@code true} si la columna se declaró en caracteres (CHAR_USED = 'C')
 * @param dataPrecision DATA_PRECISION; {@code null} si el NUMBER no tiene precisión declarada
 * @param dataScale DATA_SCALE; {@code null} si no tiene escala declarada
 */
public record AnswerColumn(
    String name,
    String dataType,
    int dataLength,
    int charLength,
    boolean charSemantics,
    Integer dataPrecision,
    Integer dataScale) {}
