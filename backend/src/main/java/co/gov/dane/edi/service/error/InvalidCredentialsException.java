package co.gov.dane.edi.service.error;

/** Mismo mensaje para usuario inexistente y clave errada: no se revela qué usuarios existen. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Usuario o clave incorrectos.");
  }
}
