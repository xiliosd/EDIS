package co.gov.dane.edi.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** La página más larga tiene decenas de preguntas: 200 respuestas es un tope holgado. */
public record PageAnswersRequest(
    @NotNull(message = "Envíe las respuestas")
        @Size(max = 200, message = "Envíe como máximo 200 respuestas por página")
        Map<String, String> answers) {}
