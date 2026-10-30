package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.model.InputType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pregunta nueva de un año en borrador. {@code afterCapProyId} nulo la deja de primera; {@code
 * columnType} y {@code columnLength} (VARCHAR2 1–4000 o NUMBER con precisión 1–38) son opcionales
 * y, si se envían, deben ir juntos; sin ellos el script deduce el tipo de la pregunta.
 */
public record NewQuestionRequest(
    @NotBlank(message = "Escriba el código de la columna.") String columnCode,
    @NotBlank(message = "Escriba el numeral.") String numeral,
    @NotBlank(message = "Escriba el texto.")
        @Size(max = 4000, message = "El texto admite hasta 4000 caracteres.")
        String text,
    @NotNull(message = "Elija el tipo de respuesta.") InputType inputType,
    Integer optionGroup,
    Long afterCapProyId,
    String columnType,
    Integer columnLength,
    @NotNull(message = "Falta la versión que leyó.") Integer version) {}
