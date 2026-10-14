package co.gov.dane.edi.model;

/** Cuenta de administrador heredada. La clave nunca debe registrarse ni mostrarse. */
public record AdminAccount(
    String username, String storedPassword, int legacyRole, String departmentCode) {

  @Override
  public String toString() {
    return "AdminAccount[username=" + username + ", legacyRole=" + legacyRole + "]";
  }
}
