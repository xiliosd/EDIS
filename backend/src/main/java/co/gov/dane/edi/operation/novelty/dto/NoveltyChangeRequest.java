package co.gov.dane.edi.operation.novelty.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Asignar o modificar la novedad de un funcionario, identificado por cédula ({@code C_C}) y año,
 * nunca por TC2. La cédula va en el cuerpo, no en la URL (las URL quedan en los logs de acceso).
 * {@code expectedNoveltyCode} y {@code expectedOtherCause} son los valores que la pantalla leyó
 * (nulos si no tenía novedad): si cambiaron entretanto, la operación responde 409.
 */
public record NoveltyChangeRequest(
    @Min(1) Integer anio,
    @NotBlank @Size(max = 11) String respondentCode,
    int noveltyCode,
    String otherCause,
    Integer expectedNoveltyCode,
    String expectedOtherCause) {}
