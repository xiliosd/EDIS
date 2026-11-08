package co.gov.dane.edi.operation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros de la operación de campo. {@code cutsDir} es un directorio persistente (volumen) donde
 * se guardan los cortes de cobertura; {@code noveltyOtherCauseCode} es el código de GRAL_NOVEDAD
 * que exige un texto de causa (el README pide verificarlo contra el catálogo real).
 */
@Validated
@ConfigurationProperties("app.operation")
public record OperationProperties(@NotBlank String cutsDir, @Min(0) int noveltyOtherCauseCode) {}
