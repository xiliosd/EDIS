package co.gov.dane.edi.operation.report.dto;

import java.util.List;

public record DepartmentCoverage(
    int code, String name, List<EntityCoverage> entities, CoverageTotals totals) {}
