package co.gov.dane.edi.admin;

/** El paquete supera el tamaño máximo de la importación (HTTP 413). */
public class PackageTooLargeException extends RuntimeException {

  public static final String MESSAGE = "El paquete supera 5 MB.";

  public PackageTooLargeException() {
    super(MESSAGE);
  }
}
