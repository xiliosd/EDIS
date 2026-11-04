package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.admin.PackageKind;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code manifiesto.json} del paquete de promoción: qué año y versión trae, las filas y la suma de
 * cada tabla FRM_* tal como quedan tras ejecutar {@code frm.sql}, las columnas de respuesta que
 * deben existir y el SHA-256 de los otros tres archivos. {@code juegoCaracteres} es el
 * NLS_CHARACTERSET de la base donde se generó.
 */
public record PackageManifest(
    @JsonProperty("encuesta") String survey,
    @JsonProperty("anio") int year,
    @JsonProperty("version") int version,
    @JsonProperty("tipo") PackageKind kind,
    @JsonProperty("generado") Instant generatedAt,
    @JsonProperty("generadoPor") String generatedBy,
    @JsonProperty("juegoCaracteres") String characterSet,
    @JsonProperty("tablas") Map<String, TableSum> tables,
    @JsonProperty("columnasRequeridas") Map<String, List<String>> requiredColumns,
    @JsonProperty("archivos") Map<String, String> files) {

  /** Filas de una tabla FRM_* del año y la suma de sus hashes de fila. */
  public record TableSum(@JsonProperty("filas") int rows, @JsonProperty("suma") String sum) {}

  public PackageManifest {
    tables = copyOf(tables);
    requiredColumns = copyOf(requiredColumns);
    files = copyOf(files);
  }

  /** Copia que conserva el orden de inserción: el JSON sale siempre con el mismo orden. */
  private static <V> Map<String, V> copyOf(Map<String, V> map) {
    return map == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(map));
  }
}
