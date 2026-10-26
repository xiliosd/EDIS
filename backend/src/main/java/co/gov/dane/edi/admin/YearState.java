package co.gov.dane.edi.admin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Estado de un año del cuestionario: solo un borrador admite ediciones estructurales. */
public enum YearState {
  BORRADOR("borrador"),
  PUBLICADO("publicado");

  private final String jsonValue;

  YearState(String jsonValue) {
    this.jsonValue = jsonValue;
  }

  @JsonValue
  public String jsonValue() {
    return jsonValue;
  }

  @JsonCreator
  public static YearState fromJson(String value) {
    return Arrays.stream(values())
        .filter(state -> state.jsonValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Estado de año desconocido: " + value));
  }
}
