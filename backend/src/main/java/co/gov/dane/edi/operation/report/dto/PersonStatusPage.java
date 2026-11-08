package co.gov.dane.edi.operation.report.dto;

import java.util.List;

/** Una página del listado nominal; {@code total} cuenta todas las filas del filtro. */
public record PersonStatusPage(List<PersonStatusRow> rows, int page, int size, long total) {}
