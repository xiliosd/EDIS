package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Constancia de la publicación de un año: cuándo, quién, qué versión del archivo y los avisos que
 * no bloquearon (cada uno como {@code CODIGO@elemento}).
 */
public record Publication(
    @JsonProperty("fecha") Instant publishedAt,
    @JsonProperty("usuario") String user,
    @JsonProperty("version") int version,
    @JsonProperty("avisos") List<String> warnings) {

  public Publication {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
