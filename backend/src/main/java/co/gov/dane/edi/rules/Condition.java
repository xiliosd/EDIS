package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Condición de un salto. Se usa exactamente una forma: {@code pregunta}, {@code todas} o {@code
 * alguna} (con {@code op}), o bien {@code y} / {@code o} con condiciones anidadas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Condition(
    @JsonProperty("pregunta") String question,
    @JsonProperty("op") String op,
    @JsonProperty("valor") String value,
    @JsonProperty("valores") List<String> values,
    @JsonProperty("todas") List<String> all,
    @JsonProperty("alguna") List<String> any,
    @JsonProperty("y") List<Condition> and,
    @JsonProperty("o") List<Condition> or) {}
