package co.gov.dane.edi.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotNull(message = "Seleccione el tipo de usuario") AccountType accountType,
    @NotBlank(message = "Ingrese su usuario") @Size(max = 50) String username,
    @NotBlank(message = "Ingrese su clave") @Size(max = 100) String password) {

  @Override
  public String toString() {
    return "LoginRequest[accountType=" + accountType + "]";
  }
}
