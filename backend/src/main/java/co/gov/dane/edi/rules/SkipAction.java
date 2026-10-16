package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SkipAction(
    @JsonProperty("accion") String action, @JsonProperty("preguntas") List<String> questions) {}
