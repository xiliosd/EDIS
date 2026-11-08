package co.gov.dane.edi.operation.report.dto;

import co.gov.dane.edi.operation.report.ReportSurvey;
import java.time.OffsetDateTime;
import java.util.List;

/** Cobertura por departamento y entidad con el consolidado nacional (del alcance consultado). */
public record CoverageReport(
    ReportSurvey survey,
    int year,
    OffsetDateTime generatedAt,
    List<DepartmentCoverage> departments,
    int departmentCount,
    int entityCount,
    boolean coversAllDepartments,
    CoverageTotals national,
    List<LevelCoverage> nationalWebLevels) {}
