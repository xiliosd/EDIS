package co.gov.dane.edi.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Preguntas de la página (FRM_CAP_PROY) en su nuevo orden y la versión que el cliente leyó. */
public record ReorderRequest(
    @NotEmpty(message = "Indique las preguntas de la página.") List<@NotNull Long> capProyIds,
    @NotNull(message = "Falta la versión que leyó.") Integer version) {}
