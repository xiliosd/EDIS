package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.rules.SkipRule;
import jakarta.validation.constraints.NotNull;

/** Una regla de salto y la versión del archivo del año que el cliente leyó. */
public record SkipRuleRequest(
    @NotNull(message = "Falta el contenido.") SkipRule rule,
    @NotNull(message = "Falta la versión que leyó.") Integer version) {}
