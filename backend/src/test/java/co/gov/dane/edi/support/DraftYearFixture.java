package co.gov.dane.edi.support;

import co.gov.dane.edi.admin.FrmTable;
import co.gov.dane.edi.admin.YearState;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Año 2027 en borrador para las pruebas del editor: copia las filas FICTICIAS de EDIN 2026 en filas
 * propias de 2027 (FRM_CAP_PROY, periodos, objetos, encabezados, ayudas y saltos) que siguen
 * compartiendo FRM_PRE_GRUPO y FRM_PREGUNTA con 2026, y crea EDIN-2027.json con estado borrador. La
 * tarea de copia de año reemplazará este apoyo por la copia real.
 */
public final class DraftYearFixture {

  /**
   * Los ids copiados quedan por encima de los del cuestionario de desarrollo y de otras pruebas.
   */
  public static final long OFFSET = 950000;

  private final JdbcTemplate jdbc;
  private final RulesFileRepository rulesFiles;
  private final SurveyProperties survey;
  private final Map<FrmTable, Long> baseline = new EnumMap<>(FrmTable.class);

  public DraftYearFixture(
      JdbcTemplate jdbc, RulesFileRepository rulesFiles, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.rulesFiles = rulesFiles;
    this.survey = survey;
  }

  /** Id de FRM_CAP_PROY de 2027 que corresponde a uno de 2026. */
  public static long draftCapProy(long capProy2026) {
    return OFFSET + capProy2026;
  }

  public void install() {
    remove();
    for (FrmTable table : FrmTable.values()) {
      Long maximum =
          jdbc.queryForObject(
              "SELECT NVL(MAX(" + table.idColumn() + "), 0) FROM " + table.tableName(), Long.class);
      baseline.put(table, maximum == null ? 0 : maximum);
    }
    jdbc.update(
        "INSERT INTO FRM_CAP_PROY (COD_CAP_PROY, CAPITULO, NUMERAL, COD_PRE_GRUPO, PROY, ANIO)"
            + " SELECT COD_CAP_PROY + ?, CAPITULO, NUMERAL, COD_PRE_GRUPO, PROY, '2027'"
            + " FROM FRM_CAP_PROY WHERE PROY = 'EDIN' AND ANIO = '2026'",
        OFFSET);
    jdbc.update(
        "INSERT INTO FRM_CAP_PERIODO (ID_PERIODO, ANIO, PROY, COD_CAP_PROY, NUMERAL)"
            + " SELECT ID_PERIODO + ?, 2027, PROY, COD_CAP_PROY + ?, NUMERAL"
            + " FROM FRM_CAP_PERIODO WHERE PROY = 'EDIN' AND ANIO = 2026",
        OFFSET,
        OFFSET);
    jdbc.update(
        "INSERT INTO FRM_OBJ_PRE (COD_OBJ_PRE, COD_GRUPO, GRUPO, COD_OBJ, COD_CAP_PROY)"
            + " SELECT COD_OBJ_PRE + ?, COD_GRUPO, GRUPO, COD_OBJ, COD_CAP_PROY + ?"
            + " FROM FRM_OBJ_PRE WHERE COD_CAP_PROY IN"
            + " (SELECT COD_CAP_PROY FROM FRM_CAP_PROY WHERE PROY = 'EDIN' AND ANIO = '2026')",
        OFFSET,
        OFFSET);
    jdbc.update(
        "INSERT INTO FRM_SALTO (COD_SAL, COD_CAP_PROY, SALTO, VALOR_SALTO)"
            + " SELECT COD_SAL + ?, COD_CAP_PROY + ?, SALTO, VALOR_SALTO FROM FRM_SALTO"
            + " WHERE COD_CAP_PROY IN"
            + " (SELECT COD_CAP_PROY FROM FRM_CAP_PROY WHERE PROY = 'EDIN' AND ANIO = '2026')",
        OFFSET,
        OFFSET);
    jdbc.update(
        "INSERT INTO FRM_ENCABEZADO (COD_ENC, COD_CAP_PROY, TIPO, ENCABEZADO, ANIO)"
            + " SELECT COD_ENC + ?, COD_CAP_PROY + ?, TIPO, ENCABEZADO, '2027'"
            + " FROM FRM_ENCABEZADO WHERE ANIO = '2026' AND COD_CAP_PROY IN"
            + " (SELECT COD_CAP_PROY FROM FRM_CAP_PROY WHERE PROY = 'EDIN' AND ANIO = '2026')",
        OFFSET,
        OFFSET);
    jdbc.update(
        "INSERT INTO FRM_PRE_AYUDA (COD_AYU, COD_CAP_PROY, TEXTO)"
            + " SELECT COD_AYU + ?, COD_CAP_PROY + ?, TEXTO FROM FRM_PRE_AYUDA"
            + " WHERE COD_CAP_PROY IN"
            + " (SELECT COD_CAP_PROY FROM FRM_CAP_PROY WHERE PROY = 'EDIN' AND ANIO = '2026')",
        OFFSET,
        OFFSET);
    rulesFiles.save(
        new RuleSet("EDIN", 2027, 0, List.of(), Map.of(), YearState.BORRADOR, Map.of()),
        0,
        "fixture",
        "crear-borrador-prueba",
        List.of());
  }

  /** Borra lo que creó {@link #install()} y las copias que haya hecho el editor. */
  public void remove() {
    if (!baseline.isEmpty()) {
      for (FrmTable table : FrmTable.values()) {
        jdbc.update(
            "DELETE FROM " + table.tableName() + " WHERE " + table.idColumn() + " > ?",
            baseline.get(table));
      }
      baseline.clear();
    }
    Path dir = Path.of(survey.rulesDir());
    try {
      Files.deleteIfExists(dir.resolve("EDIN-2027.json"));
      Path history = dir.resolve("historial");
      if (Files.isDirectory(history)) {
        try (Stream<Path> files = Files.list(history)) {
          for (Path file :
              files.filter(f -> f.getFileName().toString().startsWith("EDIN-2027.")).toList()) {
            Files.deleteIfExists(file);
          }
        }
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    rulesFiles.evict();
  }
}
