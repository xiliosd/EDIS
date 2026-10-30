package co.gov.dane.edi.service.error;

/**
 * El año no se pudo crear por una falla del servidor (HTTP 500). El mensaje, en español, explica la
 * causa sin detalles internos y se muestra tal cual a la persona.
 */
public class YearCreationFailedException extends RuntimeException {

  public YearCreationFailedException(String detail, Throwable cause) {
    super(detail, cause);
  }
}
