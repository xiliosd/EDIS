package co.gov.dane.edi.admin;

/** La fila FRM_* que se quiere editar la usa también otro año o encuesta. */
public class SharedRowException extends RuntimeException {

  public SharedRowException(String message) {
    super(message);
  }
}
