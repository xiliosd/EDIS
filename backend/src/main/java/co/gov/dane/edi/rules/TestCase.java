package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Caso de prueba de las reglas: respuestas de una página y el resultado esperado. {@code
 * porRevisar} marca un caso cuyas preguntas cambiaron después de guardarse; es nulo (y se omite del
 * JSON) cuando no aplica.
 */
public record TestCase(
    @JsonProperty("id") String id,
    @JsonProperty("nombre") String name,
    @JsonProperty("pagina") String pageCode,
    @JsonProperty("respuestas") Map<String, String> answers,
    @JsonProperty("esperado") ExpectedOutcome expected,
    @JsonProperty("porRevisar") @JsonInclude(JsonInclude.Include.NON_NULL) Boolean needsReview) {

  public TestCase {
    answers = answers == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(answers));
    expected = expected == null ? new ExpectedOutcome(null, null) : expected;
    needsReview = Boolean.TRUE.equals(needsReview) ? Boolean.TRUE : null;
  }

  @JsonIgnore
  public boolean isNeedsReview() {
    return needsReview != null;
  }

  /** El caso usa la columna en sus respuestas, en las ocultas esperadas o en los errores. */
  public boolean mentions(String column) {
    return answers.containsKey(column)
        || expected.hidden().contains(column)
        || (expected.errors() != null && expected.errors().containsKey(column));
  }

  public TestCase withNeedsReview(boolean value) {
    return new TestCase(id, name, pageCode, answers, expected, value);
  }
}
