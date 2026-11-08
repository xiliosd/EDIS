package co.gov.dane.edi.operation.report.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fecha y hora del último guardado de un capítulo; ambas nulas si el funcionario no tiene fila en
 * ese capítulo.
 *
 * @param chapter código del capítulo, de {@code B} a {@code H}
 */
public record ChapterTimestamp(String chapter, LocalDate date, LocalTime time) {}
