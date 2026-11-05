package co.gov.dane.edi.admin;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fotografía de las filas FRM_* de un año: la clausura que parte de FRM_CAP_PERIODO. Las filas van
 * ordenadas por id y cada valor es un {@code String}, un {@code BigDecimal} o null; cualquier otro
 * tipo es un error (FRM_* no tiene columnas de fecha).
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

  private static final String FIELD_SEPARATOR = "\u001F";
  private static final String NULL_MARK = "NULL";

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

  /**
   * COD_AYU distintos (ordenados) de las ayudas de pregunta y de encabezado: las definiciones de
   * GRAL_AYUDAS que el año necesita en la base de destino. GRAL_AYUDAS no viaja en el paquete.
   */
  public List<Long> helpDefinitionCodes() {
    return Stream.of(FrmTable.PRE_AYUDA, FrmTable.ENC_AYUDA)
        .flatMap(table -> rows.getOrDefault(table, List.of()).stream())
        .map(row -> row.get("COD_AYU"))
        .filter(BigDecimal.class::isInstance)
        .map(code -> ((BigDecimal) code).longValueExact())
        .distinct()
        .sorted()
        .toList();
  }

  /** Si algún texto tiene caracteres fuera de ASCII: dependen del juego de caracteres. */
  public boolean hasNonAsciiText() {
    return rows.values().stream()
        .flatMap(List::stream)
        .flatMap(row -> row.values().stream())
        .anyMatch(value -> value instanceof String text && text.chars().anyMatch(c -> c > 0x7F));
  }

  /**
   * SHA-256 de {@code COL=<largo>:<valor>} unidos con U+001F en el orden de {@link
   * FrmTable#columns()}; un nulo es {@code COL=NULL}, sin largo. El largo (en unidades UTF-16)
   * evita que un separador dentro de un valor, o un texto "NULL", se confunda con otra fila.
   */
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
      case BigDecimal number -> withLength(number.stripTrailingZeros().toPlainString());
      case String text -> withLength(text);
      default ->
          throw new IllegalArgumentException(
              "Tipo de valor no admitido en FRM_*: " + value.getClass().getSimpleName());
    };
  }

  private static String withLength(String text) {
    return text.length() + ":" + text;
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
