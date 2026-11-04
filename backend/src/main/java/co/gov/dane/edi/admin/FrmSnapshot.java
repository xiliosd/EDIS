package co.gov.dane.edi.admin;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fotografía de las filas FRM_* de un año: la clausura que parte de FRM_CAP_PERIODO. Las filas van
 * ordenadas por id y cada valor es un {@code String}, un {@code BigDecimal} o null (un {@code
 * LocalDateTime} solo si apareciera una columna de fecha, que FRM_* no tiene).
 *
 * <p>Los hashes no dependen del orden de lectura: el paquete de promoción los compara con los de la
 * base de producción para saber si {@code frm.sql} ya se ejecutó.
 */
public record FrmSnapshot(String project, int year, Map<FrmTable, List<Map<String, Object>>> rows) {

  /** Tablas de la fotografía en orden de dependencia: así las escribe {@code frm.sql}. */
  public static final List<FrmTable> TABLES =
      List.of(
          FrmTable.PREGUNTA,
          FrmTable.PRE_GRUPO,
          FrmTable.CAP_PROY,
          FrmTable.CAP_PERIODO,
          FrmTable.ENCABEZADO,
          FrmTable.PRE_AYUDA,
          FrmTable.OBJ_PRE,
          FrmTable.ENC_AYUDA);

  static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static final String FIELD_SEPARATOR = "\u001F";
  private static final String NULL_MARK = "\u0000";

  public FrmSnapshot {
    Map<FrmTable, List<Map<String, Object>>> copy = new EnumMap<>(FrmTable.class);
    rows.forEach(
        (table, tableRows) ->
            copy.put(
                table,
                tableRows.stream()
                    .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                    .toList()));
    rows = Collections.unmodifiableMap(copy);
  }

  /** SHA-256 de {@code COL=valor} unidos con U+001F en el orden de {@link FrmTable#columns()}. */
  public static String rowHash(FrmTable table, Map<String, Object> row) {
    String canonical =
        table.columns().stream()
            .map(column -> column + "=" + canonicalValue(row.get(column)))
            .collect(Collectors.joining(FIELD_SEPARATOR));
    return sha256(canonical);
  }

  /** SHA-256 de los hashes de fila ordenados y unidos con salto de línea. */
  public static String tableSum(List<String> rowHashes) {
    return sha256(rowHashes.stream().sorted().collect(Collectors.joining("\n")));
  }

  private static String canonicalValue(Object value) {
    return switch (value) {
      case null -> NULL_MARK;
      case BigDecimal number -> number.stripTrailingZeros().toPlainString();
      case String text -> text;
      case LocalDateTime timestamp -> timestamp.format(TIMESTAMP_FORMAT);
      default ->
          throw new IllegalArgumentException(
              "Tipo de valor no admitido en FRM_*: " + value.getClass().getSimpleName());
    };
  }

  private static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("La JVM no tiene SHA-256", exception);
    }
  }
}
