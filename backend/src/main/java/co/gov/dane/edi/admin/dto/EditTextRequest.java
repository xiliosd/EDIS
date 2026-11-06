package co.gov.dane.edi.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Nuevo texto (plano) y la versión del archivo del año que el cliente leyó. {@code motivo} es
 * opcional: solo lo exige una corrección sobre un año publicado (10 a 500 caracteres).
 *
 * <p>1000 es el mayor de los límites (el texto de la pregunta); el servicio aplica el de cada campo
 * (encabezado 500, palabra de ayuda 100) sobre el texto ya convertido a Latin-9.
 */
public record EditTextRequest(
    @NotBlank(message = "Escriba el texto.")
        @Size(max = 1000, message = "El texto admite hasta 1000 caracteres.")
        String text,
    @NotNull(message = "Falta la versión que leyó.") Integer version,
    String motivo) {}
