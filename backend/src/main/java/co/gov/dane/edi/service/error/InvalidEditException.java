package co.gov.dane.edi.service.error;

/** Edición del cuestionario que no se puede procesar tal como llegó (HTTP 422). */
public class InvalidEditException extends RuntimeException {

  public InvalidEditException(String detail) {
    super(detail);
  }
}
