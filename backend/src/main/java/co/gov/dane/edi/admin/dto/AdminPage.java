package co.gov.dane.edi.admin.dto;

import java.util.List;

/**
 * Una página del editor. {@code tableMissing}: en un año en borrador, la tabla asignada al capítulo
 * aún no existe en el esquema; sus preguntas quedan pendientes hasta que se cree con el script.
 */
public record AdminPage(
    String pageCode,
    String chapterCode,
    String tableName,
    boolean tableMissing,
    List<AdminEntry> entries) {}
