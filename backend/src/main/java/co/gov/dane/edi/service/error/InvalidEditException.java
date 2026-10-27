package co.gov.dane.edi.service.error;

/** Edición del cuestionario que no se puede procesar tal como llegó (HTTP 422). */
public class InvalidEditException extends RuntimeException {

  private final String field;

  public InvalidEditException(String detail) {
    this(null, detail);
  }

  /** {@code field}: campo de la solicitud al que se refiere el error (puede ser nulo). */
  public InvalidEditException(String field, String detail) {
    super(detail);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
