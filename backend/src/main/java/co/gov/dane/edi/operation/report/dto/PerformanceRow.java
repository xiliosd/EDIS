package co.gov.dane.edi.operation.report.dto;

import java.util.List;

/**
 * Rendimiento de un funcionario que inició (B07 no nulo). Contiene datos personales (Ley 1581 de
 * 2012): solo viaja en respuestas {@code no-store} y nunca se registra en logs.
 *
 * @param chapters siempre los siete capítulos B a H en ese orden; un capítulo sin fila trae fecha y
 *     hora nulas
 */
public record PerformanceRow(
    long entityCode,
    String entityName,
    String respondentCode,
    String name,
    Integer level,
    List<ChapterTimestamp> chapters) {}
