package co.gov.dane.edi.operation.report.dto;

import co.gov.dane.edi.operation.report.SampleStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Un funcionario de la muestra con su estado. Contiene datos personales (Ley 1581 de 2012): solo
 * viaja en respuestas {@code no-store} y nunca se registra en logs.
 *
 * @param loadType {@code WEB} o {@code DMC}
 * @param completedAt fecha de finalización (capítulo H) cuando el estado es {@code COMPLETE}
 * @param missingChapters capítulos B a H sin ninguna fila para el funcionario
 */
public record PersonStatusRow(
    long entityCode,
    String entityName,
    String respondentCode,
    String name,
    Integer level,
    String loadType,
    SampleStatus status,
    LocalDateTime completedAt,
    Integer noveltyCode,
    String noveltyName,
    String otherCause,
    List<String> missingChapters) {}
