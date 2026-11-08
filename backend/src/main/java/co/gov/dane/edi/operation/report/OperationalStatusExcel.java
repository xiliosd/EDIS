package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.operation.excel.XlsxSheet;
import co.gov.dane.edi.operation.report.dto.PersonStatusRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hoja "Estado operativo" del listado nominal (datos personales: descarga {@code no-store}). */
public final class OperationalStatusExcel {

  private static final List<String> HEADERS =
      List.of(
          "Código entidad",
          "Entidad",
          "Cédula",
          "Nombre",
          "Nivel",
          "Tipo de carga",
          "Estado",
          "Fecha de finalización",
          "Código novedad",
          "Novedad",
          "Otra causa",
          "Capítulos faltantes");

  private OperationalStatusExcel() {}

  public static XlsxSheet sheet(
      List<PersonStatusRow> rows, ReportSurvey reportSurvey, int year, int departmentCode) {
    List<List<Object>> values = new ArrayList<>();
    for (PersonStatusRow row : rows) {
      List<Object> cells = new ArrayList<>();
      cells.add(row.entityCode());
      cells.add(row.entityName());
      cells.add(row.respondentCode());
      cells.add(row.name());
      cells.add(row.level());
      cells.add(row.loadType());
      cells.add(label(row.status()));
      cells.add(row.completedAt());
      cells.add(row.noveltyCode());
      cells.add(row.noveltyName());
      cells.add(row.otherCause());
      cells.add(String.join(", ", row.missingChapters()));
      values.add(cells);
    }
    String title =
        "Estado de mi operativo - "
            + reportSurvey.name().toLowerCase(Locale.ROOT)
            + " "
            + year
            + " - departamento "
            + departmentCode;
    return new XlsxSheet("Estado operativo", title, HEADERS, values);
  }

  private static String label(SampleStatus status) {
    return switch (status) {
      case COMPLETE -> "Completo";
      case NOVELTY -> "Con novedad";
      case INCOMPLETE -> "Incompleto";
      case NOT_STARTED -> "Sin diligenciar";
    };
  }
}
