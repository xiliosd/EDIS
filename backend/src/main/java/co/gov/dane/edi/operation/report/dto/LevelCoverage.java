package co.gov.dane.edi.operation.report.dto;

/** Cobertura de un nivel de cargo (1 a 3). */
public record LevelCoverage(int level, CoverageTotals totals) {}
