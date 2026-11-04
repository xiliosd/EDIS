package co.gov.dane.edi.rules;

import java.time.Instant;

/**
 * Una versión del archivo de reglas del historial. Las copias anteriores a los metadatos de último
 * cambio traen usuario, operación y motivo nulos, y la fecha sale del nombre de la copia.
 */
public record HistoryEntry(
    int version, Instant savedAt, String user, String operation, String reason, boolean current) {}
