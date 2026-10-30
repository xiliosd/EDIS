package co.gov.dane.edi.support;

import co.gov.dane.edi.admin.FrmTable;
import co.gov.dane.edi.admin.YearCopyService;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.rules.RulesFileRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Año 2027 en borrador para las pruebas del editor, creado con la copia real ({@link
 * YearCopyService}) a partir de EDIN 2026. Encima de la copia agrega un paso propio que la copia no
 * produce: tres preguntas propias de 2027 en H1 para tener dos bloques de GRUPO en la misma página.
 */
public final class DraftYearFixture {

  /** Ids fijos de las filas propias de las pruebas: muy por encima de los que asigna MAX+1. */
  public static final long OFFSET = 950000;

  /** Preguntas propias de 2027 en la página H1: dos bloques de GRUPO (1 y 2) de dos cada uno. */
  public static final long H1_GROUP_1_SECOND = OFFSET + 10001;

  public static final long H1_GROUP_2_FIRST = OFFSET + 10002;
  public static final long H1_GROUP_2_SECOND = OFFSET + 10003;

  private static final List<Integer> TEST_YEARS = List.of(2027, 2028, 2029);

  private final JdbcTemplate jdbc;
  private final RulesFileRepository rulesFiles;
  private final SurveyProperties survey;
  private final YearCopyService yearCopy;
  private final Map<FrmTable, Long> baseline = new EnumMap<>(FrmTable.class);
  private final Map<Long, Long> draftCapProys = new HashMap<>();

  public DraftYearFixture(
      JdbcTemplate jdbc,
      RulesFileRepository rulesFiles,
      SurveyProperties survey,
      YearCopyService yearCopy) {
    this.jdbc = jdbc;
    this.rulesFiles = rulesFiles;
    this.survey = survey;
    this.yearCopy = yearCopy;
  }

  /** Copia 2026 en 2027 (borrador) y agrega las preguntas propias de H1. */
  public void install() {
    captureBaseline();
    yearCopy.createYear("EDIN", 2027, 2026, "fixture");
    // Se guarda al instalar: algunas pruebas cambian luego el numeral de la copia.
    jdbc.query(
        "SELECT SRC.COD_CAP_PROY, DST.COD_CAP_PROY FROM FRM_CAP_PROY SRC"
            + " JOIN FRM_CAP_PROY DST ON DST.CAPITULO = SRC.CAPITULO AND DST.NUMERAL = SRC.NUMERAL"
            + " AND DST.PROY = SRC.PROY AND DST.ANIO = '2027'"
            + " WHERE SRC.PROY = 'EDIN' AND SRC.ANIO = '2026'",
        rows -> {
          draftCapProys.put(rows.getLong(1), rows.getLong(2));
        });
    insertH1Block(H1_GROUP_1_SECOND, 1, 2, "H02A.");
    insertH1Block(H1_GROUP_2_FIRST, 2, 1, "H02B.");
    insertH1Block(H1_GROUP_2_SECOND, 2, 2, "H02C.");
  }

  /**
   * Limpia restos de otra prueba y anota el máximo id de cada tabla FRM_*: {@link #remove()} borra
   * todo lo creado después.
   */
  public void captureBaseline() {
    remove();
    for (FrmTable table : FrmTable.values()) {
      jdbc.update(
          "DELETE FROM " + table.tableName() + " WHERE " + table.idColumn() + " >= ?", OFFSET);
    }
    for (FrmTable table : FrmTable.values()) {
      Long maximum =
          jdbc.queryForObject(
              "SELECT NVL(MAX(" + table.idColumn() + "), 0) FROM " + table.tableName(), Long.class);
      baseline.put(table, maximum == null ? 0 : maximum);
    }
  }

  /** Id de FRM_CAP_PROY de 2027 que la copia creó para uno de 2026. */
  public long draftCapProy(long capProy2026) {
    Long copy = draftCapProys.get(capProy2026);
    if (copy == null) {
      throw new IllegalArgumentException("La copia no tiene la pregunta " + capProy2026);
    }
    return copy;
  }

  /** COD_ENC de 2027 copiado de un encabezado de 2026 (la copia conserva el orden por COD_ENC). */
  public long draftHeader(long codEnc2026) {
    long capProy2026 =
        jdbc.queryForObject(
            "SELECT COD_CAP_PROY FROM FRM_ENCABEZADO WHERE COD_ENC = ?", Long.class, codEnc2026);
    String sql =
        "SELECT COD_ENC FROM FRM_ENCABEZADO WHERE COD_CAP_PROY = ? AND ANIO = ? ORDER BY COD_ENC";
    List<Long> before = jdbc.queryForList(sql, Long.class, capProy2026, "2026");
    List<Long> after = jdbc.queryForList(sql, Long.class, draftCapProy(capProy2026), "2027");
    return after.get(before.indexOf(codEnc2026));
  }

  /** COD_AYU de 2027 copiado de una ayuda de pregunta de 2026. */
  public long draftHelp(long codAyu2026) {
    long capProy2026 =
        jdbc.queryForObject(
            "SELECT COD_CAP_PROY FROM FRM_PRE_AYUDA WHERE COD_AYU = ?", Long.class, codAyu2026);
    String sql = "SELECT COD_AYU FROM FRM_PRE_AYUDA WHERE COD_CAP_PROY = ? ORDER BY COD_AYU";
    List<Long> before = jdbc.queryForList(sql, Long.class, capProy2026);
    List<Long> after = jdbc.queryForList(sql, Long.class, draftCapProy(capProy2026));
    return after.get(before.indexOf(codAyu2026));
  }

  /** Pregunta propia (pregunta, grupo, cap_proy y periodo con el mismo id) de H1 en 2027. */
  private void insertH1Block(long id, int group, int order, String numeral) {
    jdbc.update(
        "INSERT INTO FRM_PREGUNTA (COD_PRE, PREGUNTA, TIPO) VALUES (?, ?, 'L')",
        id,
        "Pregunta ficticia " + numeral);
    jdbc.update(
        "INSERT INTO FRM_PRE_GRUPO (COD_PRE_GRUPO, GRUPO, ORDEN, COD_PRE) VALUES (?, ?, ?, ?)",
        id,
        group,
        order,
        id);
    jdbc.update(
        "INSERT INTO FRM_CAP_PROY (COD_CAP_PROY, CAPITULO, NUMERAL, COD_PRE_GRUPO, PROY, ANIO)"
            + " VALUES (?, 'H1', ?, ?, 'EDIN', '2027')",
        id,
        numeral,
        id);
    jdbc.update(
        "INSERT INTO FRM_CAP_PERIODO (ID_PERIODO, ANIO, PROY, COD_CAP_PROY, NUMERAL)"
            + " VALUES (?, 2027, 'EDIN', ?, ?)",
        id,
        id,
        numeral);
  }

  /** Borra lo que se creó desde {@link #captureBaseline()} y los archivos de los años de prueba. */
  public void remove() {
    if (!baseline.isEmpty()) {
      for (FrmTable table : FrmTable.values()) {
        jdbc.update(
            "DELETE FROM " + table.tableName() + " WHERE " + table.idColumn() + " > ?",
            baseline.get(table));
      }
      baseline.clear();
    }
    draftCapProys.clear();
    Path dir = Path.of(survey.rulesDir());
    try {
      for (int year : TEST_YEARS) {
        Files.deleteIfExists(dir.resolve("EDIN-" + year + ".json"));
      }
      Path history = dir.resolve("historial");
      if (Files.isDirectory(history)) {
        try (Stream<Path> files = Files.list(history)) {
          for (Path file : files.filter(DraftYearFixture::isTestYearHistory).toList()) {
            Files.deleteIfExists(file);
          }
        }
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    rulesFiles.evict();
  }

  private static boolean isTestYearHistory(Path file) {
    String name = file.getFileName().toString();
    return TEST_YEARS.stream().anyMatch(year -> name.startsWith("EDIN-" + year + "."));
  }
}
