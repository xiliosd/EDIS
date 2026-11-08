package co.gov.dane.edi.operation.report;

/** Encuesta de un reporte: sin filtro (general) o la que marca la columna ENC de la muestra. */
public enum ReportSurvey {
  GENERAL(null),
  EDI("1"),
  EDID("2");

  private final String encCode;

  ReportSurvey(String encCode) {
    this.encCode = encCode;
  }

  /** Valor de EDIN_MUESTRA.ENC que filtra la encuesta; nulo si no se filtra. */
  public String encCode() {
    return encCode;
  }
}
