package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resultado de ejecutar un caso de prueba. {@code differences} son líneas en español que explican
 * por qué un caso falla; {@code actualHidden} y {@code actualErrors} quedan vacíos cuando el caso
 * está por revisar porque no se evalúa.
 */
public record CaseResult(
    String id,
    Status status,
    List<String> actualHidden,
    Map<String, String> actualErrors,
    List<String> differences) {

  public CaseResult {
    actualHidden = List.copyOf(actualHidden);
    actualErrors = Map.copyOf(actualErrors);
    differences = List.copyOf(differences);
  }

  /** Se serializa en minúsculas, como {@code YearState}. */
  public enum Status {
    PASA,
    FALLA,
    POR_REVISAR;

    @JsonValue
    public String jsonValue() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
