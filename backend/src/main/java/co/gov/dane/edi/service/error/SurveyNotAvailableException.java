package co.gov.dane.edi.service.error;

/** El año activo aún es un borrador: el encuestado no puede usar la encuesta. */
public class SurveyNotAvailableException extends RuntimeException {

  public SurveyNotAvailableException() {
    super("La encuesta aún no está disponible.");
  }
}
