package co.gov.dane.edi.admin;

import co.gov.dane.edi.rules.AnalysisReport;

/** Las reglas candidatas agregan errores que el archivo vigente no tenía (HTTP 422). */
public class InvalidRulesException extends RuntimeException {

  private final transient AnalysisReport report;

  /** {@code report}: solo los errores nuevos y los avisos actuales. */
  public InvalidRulesException(AnalysisReport report) {
    super("Las reglas tienen errores.");
    this.report = report;
  }

  public AnalysisReport report() {
    return report;
  }
}
