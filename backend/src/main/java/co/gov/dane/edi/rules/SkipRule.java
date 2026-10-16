package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SkipRule(
    @JsonProperty("id") String id,
    @JsonProperty("descripcion") String description,
    @JsonProperty("si") Condition when,
    @JsonProperty("entonces") SkipAction then) {}
