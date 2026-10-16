package co.gov.dane.edi.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Clave del radicado: debe ser estable entre despliegues para que soporte pueda recalcularlo. */
@Validated
@ConfigurationProperties("app.receipt")
public record ReceiptProperties(@NotBlank @Size(min = 32) String key) {

  @Override
  public String toString() {
    return "ReceiptProperties[key=***]";
  }
}
