package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Resultado que un caso de prueba espera: las preguntas deshabilitadas y, opcionalmente, el error
 * de cada columna. Un {@code errores} nulo significa que los errores no se comparan (los casos
 * sembrados solo revisan las preguntas ocultas) y se omite del JSON.
 */
public record ExpectedOutcome(
    @JsonProperty("ocultas") List<String> hidden,
    @JsonProperty("errores") @JsonInclude(JsonInclude.Include.NON_NULL)
        Map<String, String> errors) {

  public ExpectedOutcome {
    hidden = hidden == null ? List.of() : hidden.stream().sorted().distinct().toList();
    errors = errors == null ? null : Collections.unmodifiableMap(new TreeMap<>(errors));
  }
}
