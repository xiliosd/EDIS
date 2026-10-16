package co.gov.dane.edi.service.error;

/** Operación incompatible con el estado actual (D16: página guardada o bloqueada). */
public class ConflictException extends RuntimeException {

  public ConflictException(String detail) {
    super(detail);
  }
}
