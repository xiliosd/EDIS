package co.gov.dane.edi.operation.keys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Generar las claves pendientes del año. {@code expectedWithoutKey} es el número de funcionarios
 * sin clave que la pantalla mostró al pedir la confirmación: si cambió entretanto, la operación
 * responde 409 y no genera nada.
 */
public record KeysGenerateRequest(
    @Min(2000) @Max(2100) int anio, @PositiveOrZero long expectedWithoutKey) {}
