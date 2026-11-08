package co.gov.dane.edi.operation.novelty.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Quitar la novedad de un funcionario (limpia IDFNOV y NOV_OTRA_CAUSA). Los esperados funcionan
 * como en {@link NoveltyChangeRequest}.
 */
public record NoveltyRemoveRequest(
    @Min(1) int anio,
    @NotBlank @Size(max = 11) String respondentCode,
    Integer expectedNoveltyCode,
    String expectedOtherCause) {}
