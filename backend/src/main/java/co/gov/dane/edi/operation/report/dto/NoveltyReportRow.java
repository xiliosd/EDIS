package co.gov.dane.edi.operation.report.dto;

/**
 * Un funcionario con novedad. Contiene datos personales (Ley 1581 de 2012): solo viaja en
 * respuestas {@code no-store} y nunca se registra en logs.
 *
 * @param noveltyName nombre del catálogo; nulo si el código no está en el catálogo
 * @param otherCause texto libre de "otra causa"
 * @param complete {@code true} si además cumple el criterio COMPLETE del estado
 */
public record NoveltyReportRow(
    int departmentCode,
    String departmentName,
    long entityCode,
    String entityName,
    String respondentCode,
    String name,
    Integer level,
    int noveltyCode,
    String noveltyName,
    String otherCause,
    boolean complete) {}
