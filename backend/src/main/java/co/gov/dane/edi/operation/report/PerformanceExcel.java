package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.operation.excel.XlsxSheet;
import co.gov.dane.edi.operation.report.dto.ChapterTimestamp;
import co.gov.dane.edi.operation.report.dto.PerformanceRow;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hoja "Rendimientos": por capítulo, la fecha y luego la hora (Fecha B, Hora B, ... Fecha H, Hora
 * H; en el PHP las de H estaban invertidas). Datos personales: descarga {@code no-store}.
 */
public final class PerformanceExcel {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  private PerformanceExcel() {}

  public static XlsxSheet sheet(
      List<PerformanceRow> rows, ReportSurvey reportSurvey, int year, Integer departmentCode) {
    List<String> headers =
        new ArrayList<>(List.of("Código entidad", "Entidad", "Cédula", "Nombre", "Nivel"));
    for (Chapter chapter : Chapter.values()) {
      headers.add("Fecha " + chapter.code());
      headers.add("Hora " + chapter.code());
    }
    List<List<Object>> values = new ArrayList<>();
    for (PerformanceRow row : rows) {
      List<Object> cells = new ArrayList<>();
      cells.add(row.entityCode());
      cells.add(row.entityName());
      cells.add(row.respondentCode());
      cells.add(row.name());
      cells.add(row.level());
      for (ChapterTimestamp chapter : row.chapters()) {
        cells.add(chapter.date());
        cells.add(chapter.time() == null ? null : chapter.time().format(TIME));
      }
      values.add(cells);
    }
    String title =
        "Rendimientos - "
            + reportSurvey.name().toLowerCase(Locale.ROOT)
            + " "
            + year
            + (departmentCode == null ? "" : " - departamento " + departmentCode);
    return new XlsxSheet("Rendimientos", title, headers, values);
  }
}
