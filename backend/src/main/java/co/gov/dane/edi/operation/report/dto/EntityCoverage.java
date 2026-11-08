package co.gov.dane.edi.operation.report.dto;

import java.util.List;

/** Una fila por entidad (web y DMC sumados); el detalle por nivel es solo de la carga web. */
public record EntityCoverage(
    long code, String name, CoverageTotals totals, List<LevelCoverage> webLevels) {}
