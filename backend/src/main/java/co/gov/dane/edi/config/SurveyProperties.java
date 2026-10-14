package co.gov.dane.edi.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Encuesta y periodo activos; el sistema atiende una sola encuesta y un solo año a la vez. */
@Validated
@ConfigurationProperties("app.survey")
public record SurveyProperties(
    @NotBlank @Pattern(regexp = "EDIN|EDID") String code,
    @Min(2020) @Max(2100) int year,
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_$#]{0,127}") String dbSchema,
    @NotBlank String rulesDir) {

  /** Tabla de muestra; el mapa es fijo porque el nombre se concatena en el SQL. */
  public String sampleTableName() {
    return switch (code) {
      case "EDIN" -> "EDIN_MUESTRA";
      case "EDID" -> "EDID_MUESTRA";
      default -> throw new IllegalStateException("Encuesta no soportada: " + code);
    };
  }

  /** Las columnas ANIO heredadas son VARCHAR2, por eso el año se compara como texto. */
  public String yearText() {
    return String.valueOf(year);
  }
}
