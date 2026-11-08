package co.gov.dane.edi.operation.novelty.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Consulta de los funcionarios de una entidad. Va en el cuerpo de un POST para que el filtro no
 * quede en la URL; {@code anio} nulo es el año de la encuesta activa.
 */
public record NoveltyPeopleRequest(
    Integer anio, @NotNull @Min(1) @Max(999) Integer departamento, @NotNull Long entidad) {}
