package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Validación de una columna; {@code obligatoria} es verdadera si no se indica. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldValidation(
    @JsonProperty("obligatoria") Boolean required,
    @JsonProperty("tipo") String type,
    @JsonProperty("min") BigDecimal min,
    @JsonProperty("max") BigDecimal max,
    @JsonProperty("longitudMax") Integer maxLength) {

  @JsonIgnore
  public boolean isRequired() {
    return required == null || required;
  }
}
