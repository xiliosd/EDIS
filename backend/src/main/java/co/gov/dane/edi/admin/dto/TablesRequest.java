package co.gov.dane.edi.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Tabla de respuestas de cada capítulo (código del capítulo a nombre de tabla) que reemplaza el
 * mapeo del año; un capítulo ausente usa su tabla fija. {@code version}: la que leyó el cliente.
 */
public record TablesRequest(
    @NotNull(message = "Indique las tablas por capítulo.") Map<String, String> tables,
    @NotNull(message = "Falta la versión que leyó.") Integer version) {}
