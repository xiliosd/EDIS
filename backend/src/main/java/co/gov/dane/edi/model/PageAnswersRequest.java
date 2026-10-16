package co.gov.dane.edi.model;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record PageAnswersRequest(
    @NotNull(message = "Envíe las respuestas") Map<String, String> answers) {}
