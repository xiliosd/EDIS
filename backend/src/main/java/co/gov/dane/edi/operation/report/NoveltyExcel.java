package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.operation.excel.XlsxSheet;
import co.gov.dane.edi.operation.report.dto.NoveltyReportRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hoja "Novedades" (datos personales: descarga {@code no-store}). */
public final class NoveltyExcel {

  private static final List<String> HEADERS =
      List.of(
          "Código departamento",
          "Departamento",
          "Código entidad",
          "Entidad",
          "Cédula",
          "Nombre",
          "Nivel",
          "Código novedad",
          "Novedad",
          "Otra causa",
          "Completo");

  private NoveltyExcel() {}

  public static XlsxSheet sheet(
      List<NoveltyReportRow> rows, ReportSurvey reportSurvey, int year, Integer departmentCode) {
    List<List<Object>> values = new ArrayList<>();
    for (NoveltyReportRow row : rows) {
      List<Object> cells = new ArrayList<>();
      cells.add(row.departmentCode());
      cells.add(row.departmentName());
      cells.add(row.entityCode());
      cells.add(row.entityName());
      cells.add(row.respondentCode());
      cells.add(row.name());
      cells.add(row.level());
      cells.add(row.noveltyCode());
      cells.add(row.noveltyName());
      cells.add(row.otherCause());
      cells.add(row.complete() ? "Sí" : "No");
      values.add(cells);
    }
    String title =
        "Novedades - "
            + reportSurvey.name().toLowerCase(Locale.ROOT)
            + " "
            + year
            + (departmentCode == null ? "" : " - departamento " + departmentCode);
    return new XlsxSheet("Novedades", title, HEADERS, values);
  }
}
