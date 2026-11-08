package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.operation.report.dto.CoverageReport;
import co.gov.dane.edi.operation.report.dto.CutSummary;
import co.gov.dane.edi.operation.report.dto.DepartmentCoverage;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Corte de cobertura de un departamento: una fotografía fija del reporte. Guarda solo cifras y
 * nombres de entidad y departamento, nunca datos de personas.
 *
 * @param id {@code yyyyMMdd-HHmmss-<6 hex>} en hora de Bogotá
 */
public record CoverageCut(
    String id,
    ReportSurvey survey,
    int year,
    int departmentCode,
    String generatedBy,
    OffsetDateTime generatedAt,
    CoverageReport report) {

  /** Colombia no usa horario de verano: el desplazamiento es fijo. */
  public static final ZoneOffset BOGOTA = ZoneOffset.ofHours(-5);

  /** Nombre del departamento tal como quedó en el reporte; sin filas, un nombre por código. */
  public String departmentName() {
    return report.departments().stream()
        .filter(department -> department.code() == departmentCode)
        .map(DepartmentCoverage::name)
        .findFirst()
        .orElse("Departamento " + departmentCode);
  }

  /** La fecha se muestra en hora de Bogotá aunque el JSON se relea en UTC. */
  public CutSummary summary() {
    return new CutSummary(
        id,
        survey,
        year,
        departmentCode,
        departmentName(),
        generatedBy,
        generatedAt.withOffsetSameInstant(BOGOTA));
  }
}
