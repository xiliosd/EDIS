package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.rules.RuleSet;
import jakarta.validation.constraints.NotNull;

/**
 * El archivo de reglas a importar y la versión del archivo del año que el cliente leyó. {@code
 * motivo} es opcional: solo lo exige una corrección sobre un año publicado (10 a 500 caracteres).
 */
public record ImportRulesRequest(
    @NotNull(message = "Falta el contenido.") RuleSet rules,
    @NotNull(message = "Falta la versión que leyó.") Integer version,
    String motivo) {}
