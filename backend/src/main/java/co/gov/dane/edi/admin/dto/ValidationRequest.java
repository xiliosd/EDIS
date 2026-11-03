package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.rules.FieldValidation;
import jakarta.validation.constraints.NotNull;

/** Una validación de columna y la versión del archivo del año que el cliente leyó. */
public record ValidationRequest(
    @NotNull(message = "Falta el contenido.") FieldValidation validation,
    @NotNull(message = "Falta la versión que leyó.") Integer version) {}
