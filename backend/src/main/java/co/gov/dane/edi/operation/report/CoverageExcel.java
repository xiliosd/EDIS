package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.operation.excel.XlsxSheet;
import co.gov.dane.edi.operation.report.dto.CoverageReport;
import co.gov.dane.edi.operation.report.dto.CoverageTotals;
import co.gov.dane.edi.operation.report.dto.DepartmentCoverage;
import co.gov.dane.edi.operation.report.dto.EntityCoverage;
import co.gov.dane.edi.operation.report.dto.LevelCoverage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Hojas de Excel de la cobertura (también las usa el corte de la Tarea 5). */
public final class CoverageExcel {

  public static final String COVERAGE_SHEET = "Cobertura";
  public static final String NATIONAL_SHEET = "Consolidado nacional";
  public static final String TOTAL_LEVEL = "Total";

  private static final List<String> METRIC_HEADERS =
      List.of(
          "Muestra",
          "Novedades",
          "Muestra efectiva",
          "Completas",
          "% completas",
          "Incompletas",
          "% incompletas",
          "Sin diligenciar",
          "% sin diligenciar");

  private CoverageExcel() {}

  public static List<XlsxSheet> sheets(CoverageReport report) {
    String suffix = report.survey() + " " + report.year();
    return List.of(coverageSheet(report, suffix), nationalSheet(report, suffix));
  }

  private static XlsxSheet coverageSheet(CoverageReport report, String suffix) {
    List<String> headers = new ArrayList<>(List.of("Departamento", "Código entidad", "Entidad"));
    headers.add("Nivel");
    headers.addAll(METRIC_HEADERS);
    List<List<Object>> rows = new ArrayList<>();
    for (DepartmentCoverage department : report.departments()) {
      for (EntityCoverage entity : department.entities()) {
        rows.add(
            row(department.name(), entity.code(), entity.name(), TOTAL_LEVEL, entity.totals()));
        for (LevelCoverage level : entity.webLevels()) {
          if (level.totals().sample() > 0) {
            rows.add(
                row(
                    department.name(),
                    entity.code(),
                    entity.name(),
                    level.level(),
                    level.totals()));
          }
        }
      }
      rows.add(
          row(
              department.name(),
              null,
              "Total " + department.name(),
              TOTAL_LEVEL,
              department.totals()));
    }
    return new XlsxSheet(COVERAGE_SHEET, "Cobertura de la muestra " + suffix, headers, rows);
  }

  private static XlsxSheet nationalSheet(CoverageReport report, String suffix) {
    List<String> headers = new ArrayList<>(List.of("Departamento", "Entidades", "Nivel"));
    headers.addAll(METRIC_HEADERS);
    List<List<Object>> rows = new ArrayList<>();
    for (DepartmentCoverage department : report.departments()) {
      rows.add(
          nationalRow(
              department.name(), department.entities().size(), TOTAL_LEVEL, department.totals()));
    }
    rows.add(nationalRow("Total nacional", report.entityCount(), TOTAL_LEVEL, report.national()));
    for (LevelCoverage level : report.nationalWebLevels()) {
      rows.add(nationalRow("Total nacional (carga web)", null, level.level(), level.totals()));
    }
    return new XlsxSheet(
        NATIONAL_SHEET, "Consolidado nacional de cobertura " + suffix, headers, rows);
  }

  private static List<Object> row(
      String department, Long entityCode, String entity, Object level, CoverageTotals totals) {
    List<Object> cells = new ArrayList<>(Arrays.asList(department, entityCode, entity, level));
    cells.addAll(metrics(totals));
    return cells;
  }

  private static List<Object> nationalRow(
      String label, Integer entities, Object level, CoverageTotals totals) {
    List<Object> cells = new ArrayList<>(Arrays.asList(label, entities, level));
    cells.addAll(metrics(totals));
    return cells;
  }

  private static List<Object> metrics(CoverageTotals totals) {
    return List.of(
        totals.sample(),
        totals.novelties(),
        totals.effectiveSample(),
        totals.complete(),
        totals.completePct(),
        totals.incomplete(),
        totals.incompletePct(),
        totals.notStarted(),
        totals.notStartedPct());
  }
}
