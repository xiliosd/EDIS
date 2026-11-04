package co.gov.dane.edi.admin;

import java.util.List;

/**
 * El paquete de promoción no se puede importar (HTTP 422): el mensaje dice qué revisión falló y
 * {@code differences} lista cada tabla, columna o archivo que no cuadra.
 */
public class PackageRejectedException extends RuntimeException {

  private final transient List<String> differences;

  public PackageRejectedException(String detail, List<String> differences) {
    super(detail);
    this.differences = List.copyOf(differences);
  }

  public List<String> differences() {
    return differences;
  }
}
