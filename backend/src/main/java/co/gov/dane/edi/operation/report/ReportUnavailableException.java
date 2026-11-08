package co.gov.dane.edi.operation.report;

/** El cuestionario del año no tiene las preguntas que definen una encuesta completa. */
public class ReportUnavailableException extends RuntimeException {

  public static final String TITLE = "Reporte no disponible";

  public ReportUnavailableException(int year) {
    super(
        "El cuestionario de "
            + year
            + " no tiene las preguntas B07 y H06 que definen una encuesta completa.");
  }
}
