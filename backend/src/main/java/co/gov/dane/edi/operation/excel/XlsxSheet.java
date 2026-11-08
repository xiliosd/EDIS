package co.gov.dane.edi.operation.excel;

import java.util.List;

/** Una hoja de cálculo: fila de título, encabezados y filas de datos (String, Number, fechas). */
public record XlsxSheet(String name, String title, List<String> headers, List<List<Object>> rows) {}
