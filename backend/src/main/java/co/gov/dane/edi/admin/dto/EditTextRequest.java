package co.gov.dane.edi.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Nuevo texto (plano) y la versión del archivo del año que el cliente leyó. */
public record EditTextRequest(
    @NotBlank(message = "Escriba el texto.")
        @Size(max = 4000, message = "El texto admite hasta 4000 caracteres.")
        String text,
    @NotNull(message = "Falta la versión que leyó.") Integer version) {}
