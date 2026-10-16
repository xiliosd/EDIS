package co.gov.dane.edi.model;

import jakarta.validation.constraints.Size;

/** Observaciones opcionales; OBS es VARCHAR2(1100) en la tabla H. */
public record FinishRequest(
    @Size(max = 1100, message = "Las observaciones admiten hasta 1100 caracteres")
        String observations) {

  @Override
  public String toString() {
    return "FinishRequest[observations=***]";
  }
}
