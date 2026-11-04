package co.gov.dane.edi.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Publicación del año; {@code version}: la que leyó el cliente. */
public record PublishRequest(@NotNull(message = "Falta la versión que leyó.") Integer version) {}
