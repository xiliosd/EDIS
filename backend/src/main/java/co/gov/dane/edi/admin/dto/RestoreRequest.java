package co.gov.dane.edi.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Restauración de una versión; {@code version}: la vigente que leyó el cliente. */
public record RestoreRequest(@NotNull(message = "Falta la versión que leyó.") Integer version) {}
