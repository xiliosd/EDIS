package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.dto.PackageManifest.TableSum;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.rules.RuleSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que la generación y la importación del paquete deben calcular igual: los nombres y el orden de
 * las entradas, el SHA-256 de cada archivo, la suma de cada tabla FRM_* y la tabla de cada
 * capítulo.
 */
final class PromotionPackageFormat {

  static final String MANIFEST = "manifiesto.json";
  static final String RULES = "reglas.json";
  static final String FRM = "frm.sql";
  static final String COLUMNS = "columnas.sql";

  /** Orden fijo de las entradas del ZIP. */
  static final List<String> ENTRIES = List.of(MANIFEST, RULES, FRM, COLUMNS);

  /** Los archivos cuyo SHA-256 lleva el manifiesto (el manifiesto no puede llevar el suyo). */
  static final List<String> HASHED_ENTRIES = List.of(RULES, FRM, COLUMNS);

  private PromotionPackageFormat() {}

  /** Filas y suma de cada tabla, con {@link FrmSnapshot#rowHash} y {@link FrmSnapshot#tableSum}. */
  static Map<String, TableSum> tableSums(FrmSnapshot snapshot) {
    Map<String, TableSum> sums = new LinkedHashMap<>();
    for (FrmTable table : FrmSnapshot.TABLES) {
      List<Map<String, Object>> rows = snapshot.rows().getOrDefault(table, List.of());
      List<String> hashes = new ArrayList<>(rows.size());
      rows.forEach(row -> hashes.add(FrmSnapshot.rowHash(table, row)));
      sums.put(table.tableName(), new TableSum(rows.size(), FrmSnapshot.tableSum(hashes)));
    }
    return sums;
  }

  /** La tabla mapeada del capítulo en esas reglas o, sin mapeo, la fija del capítulo. */
  static String tableOf(RuleSet rules, Chapter chapter) {
    String mapped = rules.tables().get(chapter.code());
    return mapped == null ? chapter.tableName() : mapped;
  }

  /** JSON con sangría, en UTF-8 sin BOM y con LF aunque el servidor corra en Windows. */
  static byte[] json(JsonMapper jsonMapper, Object value) {
    String text = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    // Un CR solo puede venir del salto de línea del sistema: dentro de un texto JSON va escapado.
    return text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
  }

  static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("La JVM no tiene SHA-256", exception);
    }
  }
}
