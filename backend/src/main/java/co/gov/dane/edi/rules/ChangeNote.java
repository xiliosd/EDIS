package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Último cambio del archivo de reglas. El motivo de una corrección vive solo aquí: nunca se envía a
 * los registros de auditoría.
 */
public record ChangeNote(
    @JsonProperty("usuario") String user,
    @JsonProperty("fecha") Instant at,
    @JsonProperty("operacion") String operation,
    @JsonProperty("motivo") @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {}
