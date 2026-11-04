package co.gov.dane.edi.admin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Clase de paquete de promoción. Un paquete de año instala un año completo y no toca filas ajenas;
 * uno de corrección además actualiza los textos (y el repunte de la copia al escribir) de las filas
 * que ya existen.
 */
public enum PackageKind {
  ANIO("anio"),
  CORRECCION("correccion");

  private final String jsonValue;

  PackageKind(String jsonValue) {
    this.jsonValue = jsonValue;
  }

  @JsonValue
  public String jsonValue() {
    return jsonValue;
  }

  @JsonCreator
  public static PackageKind fromJson(String value) {
    return Arrays.stream(values())
        .filter(kind -> kind.jsonValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Clase de paquete desconocida: " + value));
  }
}
