package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.support.DraftYearFixture;
import co.gov.dane.edi.support.FrmScriptRunner;
import co.gov.dane.edi.support.HostileTexts;
import co.gov.dane.edi.support.OracleIntegrationTest;
import co.gov.dane.edi.support.SqlPlusRunner;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ejecuta {@code frm.sql} contra la Oracle de pruebas: el 2027 que crea {@link YearCopyService} se
 * fotografía, se borran sus filas propias (las compartidas con 2026 se quedan) y el script las
 * vuelve a crear.
 */
class FrmScriptExecutionIT extends OracleIntegrationTest {

  private static final String PROJECT = "EDIN";
  private static final int YEAR = 2027;
  private static final String SOURCE_CHARSET = "AL32UTF8";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private SurveyProperties survey;
  @Autowired private YearCopyService yearCopy;
  @Autowired private QuestionnaireService questionnaire;
  @Autowired private FrmSnapshotRepository snapshots;
  @Autowired private FrmScriptWriter writer;

  private DraftYearFixture draft;
  private FrmScriptRunner runner;
  private FrmSnapshot original;

  @BeforeEach
  void installDraftYear() {
    draft = new DraftYearFixture(jdbc, rulesFiles, survey, yearCopy);
    draft.install();
    runner = new FrmScriptRunner(dataSource);
    original = snapshots.snapshot(PROJECT, YEAR);
  }

  @AfterEach
  void removeDraftYear() {
    draft.remove();
    questionnaire.evict();
  }

  @Test
  void frmScriptIsIdempotentAndStopsOnOccupiedId() throws SQLException {
    String yearScript = writer.write(original, PackageKind.ANIO, SOURCE_CHARSET);
    String correctionScript = writer.write(original, PackageKind.CORRECCION, SOURCE_CHARSET);
    deleteOwnRows(original);
    assertThat(snapshots.snapshot(PROJECT, YEAR).rows().get(FrmTable.CAP_PERIODO)).isEmpty();

    runner.run(yearScript);
    assertThat(snapshots.snapshot(PROJECT, YEAR)).isEqualTo(original);

    Map<FrmTable, Long> afterFirstRun = tableCounts();
    runner.run(yearScript);
    assertThat(snapshots.snapshot(PROJECT, YEAR)).isEqualTo(original);
    assertThat(tableCounts()).isEqualTo(afterFirstRun);

    // Una fila borrada antes del choque prueba que el rollback deshace lo ya insertado.
    long header = firstId(FrmTable.ENCABEZADO);
    jdbc.update(
        "UPDATE FRM_ENCABEZADO SET ENCABEZADO = 'Texto cambiado a mano' WHERE COD_ENC = ?", header);
    deleteRow(FrmTable.PREGUNTA, DraftYearFixture.H1_GROUP_2_FIRST);
    Map<FrmTable, Long> beforeConflict = tableCounts();

    assertThatThrownBy(() -> runner.run(yearScript))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("ORA-20001")
        .hasMessageContaining("Id ocupado: FRM_ENCABEZADO COD_ENC=" + header);
    assertThat(tableCounts()).isEqualTo(beforeConflict);
    assertThat(headerText(header)).isEqualTo("Texto cambiado a mano");

    runner.run(correctionScript);
    assertThat(snapshots.snapshot(PROJECT, YEAR)).isEqualTo(original);
  }

  @Test
  void correctionPackageAbortsWhenNonCorrectableColumnDiffers() throws SQLException {
    String correctionScript = writer.write(original, PackageKind.CORRECCION, SOURCE_CHARSET);
    long capProy = firstId(FrmTable.CAP_PROY);
    jdbc.update("UPDATE FRM_CAP_PROY SET NUMERAL = 'Z99.' WHERE COD_CAP_PROY = ?", capProy);
    deleteRow(FrmTable.PREGUNTA, DraftYearFixture.H1_GROUP_2_FIRST);
    Map<FrmTable, Long> before = tableCounts();

    assertThatThrownBy(() -> runner.run(correctionScript))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("ORA-20001")
        .hasMessageContaining("Id ocupado: FRM_CAP_PROY COD_CAP_PROY=" + capProy);
    assertThat(tableCounts()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT NUMERAL FROM FRM_CAP_PROY WHERE COD_CAP_PROY = ?", String.class, capProy))
        .isEqualTo("Z99.");
  }

  @Test
  void correctionPackageUpdatesOnlyCorrectableColumns() throws SQLException {
    String correctionScript = writer.write(original, PackageKind.CORRECCION, SOURCE_CHARSET);
    String yearScript = writer.write(original, PackageKind.ANIO, SOURCE_CHARSET);
    long question = DraftYearFixture.H1_GROUP_2_FIRST;
    long capProy = DraftYearFixture.H1_GROUP_2_SECOND;
    long header = firstId(FrmTable.ENCABEZADO);
    long help = firstId(FrmTable.PRE_AYUDA);
    long headerHelp = firstId(FrmTable.ENC_AYUDA);
    jdbc.update("UPDATE FRM_PREGUNTA SET PREGUNTA = 'Otro texto' WHERE COD_PRE = ?", question);
    jdbc.update("UPDATE FRM_ENCABEZADO SET ENCABEZADO = NULL WHERE COD_ENC = ?", header);
    jdbc.update("UPDATE FRM_PRE_AYUDA SET TEXTO = 'Otra ayuda' WHERE COD_PRE_AYU = ?", help);
    jdbc.update("UPDATE FRM_ENC_AYUDA SET TEXTO = 'Otra' WHERE COD_ENC_AYU = ?", headerHelp);
    // El repunte de la copia al escribir: el cap_proy apunta a otro grupo.
    jdbc.update(
        "UPDATE FRM_CAP_PROY SET COD_PRE_GRUPO = ? WHERE COD_CAP_PROY = ?",
        DraftYearFixture.H1_GROUP_1_SECOND,
        capProy);

    assertThatThrownBy(() -> runner.run(yearScript)).hasMessageContaining("ORA-20001");
    runner.run(correctionScript);

    assertThat(snapshots.snapshot(PROJECT, YEAR)).isEqualTo(original);
    Map<FrmTable, Long> afterCorrection = tableCounts();
    runner.run(correctionScript);
    assertThat(tableCounts()).isEqualTo(afterCorrection);
    assertThat(snapshots.snapshot(PROJECT, YEAR)).isEqualTo(original);
  }

  /** COD_AYU es parte de la fila pero no corregible: otra definición es un id ocupado. */
  @Test
  void correctionPackageAbortsWhenHelpPointsToAnotherDefinition() throws SQLException {
    String correctionScript = writer.write(original, PackageKind.CORRECCION, SOURCE_CHARSET);
    long help = firstId(FrmTable.PRE_AYUDA);
    jdbc.update("UPDATE FRM_PRE_AYUDA SET COD_AYU = 502 WHERE COD_PRE_AYU = ?", help);
    deleteRow(FrmTable.PREGUNTA, DraftYearFixture.H1_GROUP_2_FIRST);
    Map<FrmTable, Long> before = tableCounts();

    assertThatThrownBy(() -> runner.run(correctionScript))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("ORA-20001")
        .hasMessageContaining("Id ocupado: FRM_PRE_AYUDA COD_PRE_AYU=" + help);
    assertThat(tableCounts()).isEqualTo(before);
  }

  /**
   * GRAL_AYUDAS no viaja en el paquete: si en el destino falta la definición de una ayuda, el
   * script aborta con ORA-20003 antes de escribir nada.
   */
  @Test
  void missingHelpDefinitionStopsBeforeAnyInsert() throws SQLException {
    String yearScript = writer.write(original, PackageKind.ANIO, SOURCE_CHARSET);
    Map<String, Object> definition =
        jdbc.queryForMap(
            "SELECT PROY, COD, TITULO, DESCRIPCION, ANIO FROM GRAL_AYUDAS WHERE COD = 501");
    deleteOwnRows(original);
    jdbc.update("DELETE FROM GRAL_AYUDAS WHERE COD = 501");
    try {
      Map<FrmTable, Long> before = tableCounts();

      assertThatThrownBy(() -> runner.run(yearScript))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("ORA-20003")
          .hasMessageContaining("Falta la definicion COD_AYU 501 en GRAL_AYUDAS");
      assertThat(tableCounts()).isEqualTo(before);
    } finally {
      jdbc.update(
          "INSERT INTO GRAL_AYUDAS (PROY, COD, TITULO, DESCRIPCION, ANIO) VALUES (?, ?, ?, ?, ?)",
          definition.get("PROY"),
          definition.get("COD"),
          definition.get("TITULO"),
          definition.get("DESCRIPCION"),
          definition.get("ANIO"));
    }
    runner.run(yearScript);
    assertThat(snapshots.snapshot(PROJECT, YEAR)).isEqualTo(original);
  }

  /**
   * Los textos hostiles van por el SQL*Plus real del contenedor con el cliente en WE8MSWIN1252: el
   * script ASCII los recrea idénticos, y un id ocupado sale con error y sin cambios.
   */
  @Test
  void hostileTextsRoundTripThroughOracle() throws Exception {
    List<long[]> helpSlots = new ArrayList<>();
    original
        .rows()
        .get(FrmTable.PRE_AYUDA)
        .forEach(row -> helpSlots.add(slot(FrmTable.PRE_AYUDA, row)));
    List<long[]> questionSlots = new ArrayList<>();
    original.rows().get(FrmTable.PREGUNTA).stream()
        .filter(row -> ((BigDecimal) row.get("COD_PRE")).longValue() >= DraftYearFixture.OFFSET)
        .forEach(row -> questionSlots.add(slot(FrmTable.PREGUNTA, row)));
    // Oracle Free (AL32UTF8) guarda el NUL tal cual: entra en la prueba como un texto más.
    assertThat(oracleKeepsNul(questionSlots.get(questionSlots.size() - 1))).isTrue();
    List<String> texts = new ArrayList<>(HostileTexts.all());
    // TEXTO de FRM_PRE_AYUDA es VARCHAR2(100 BYTE): los textos largos van a PREGUNTA (1000).
    List<long[]> slots = new ArrayList<>();
    for (String text : texts) {
      boolean fitsHelp = text.getBytes(StandardCharsets.UTF_8).length <= 100;
      List<long[]> source = fitsHelp && !helpSlots.isEmpty() ? helpSlots : questionSlots;
      assertThat(source).as("faltan filas para los textos hostiles").isNotEmpty();
      slots.add(source.remove(0));
    }
    for (int index = 0; index < texts.size(); index++) {
      writeText(slots.get(index), texts.get(index));
    }
    FrmSnapshot hostile = snapshots.snapshot(PROJECT, YEAR);
    List<Object> stored = new ArrayList<>();
    for (int index = 0; index < texts.size(); index++) {
      stored.add(readText(slots.get(index)));
    }
    assertThat(stored).containsExactlyElementsOf(texts);
    String yearScript = writer.write(hostile, PackageKind.ANIO, SOURCE_CHARSET);
    SqlPlusRunner sqlPlus = new SqlPlusRunner(ORACLE);
    deleteOwnRows(hostile);

    SqlPlusRunner.Run first = sqlPlus.run(yearScript);
    assertThat(first.exitCode()).as(first.output()).isZero();
    assertThat(first.output()).doesNotContain("SP2-").doesNotContain("ORA-");
    assertThat(snapshots.snapshot(PROJECT, YEAR)).isEqualTo(hostile);

    writeText(slots.get(0), "Texto cambiado a mano");
    deleteRow(FrmTable.PREGUNTA, DraftYearFixture.H1_GROUP_2_FIRST);
    Map<FrmTable, Long> beforeConflict = tableCounts();
    SqlPlusRunner.Run conflict = sqlPlus.run(yearScript);
    assertThat(conflict.exitCode()).isNotZero();
    assertThat(conflict.output()).contains("ORA-20001").contains("Id ocupado: FRM_PRE_AYUDA");
    assertThat(tableCounts()).isEqualTo(beforeConflict);
  }

  /**
   * Un paquete con texto no ASCII generado en una base WE8MSWIN1252 no se aplica en esta
   * (AL32UTF8): el primer bloque aborta con ORA-20002 antes de escribir nada. Con el mismo juego de
   * caracteres pasa.
   */
  @Test
  void characterSetCheckStopsBeforeAnyInsert() throws Exception {
    long[] help = slot(FrmTable.PRE_AYUDA, original.rows().get(FrmTable.PRE_AYUDA).get(0));
    writeText(help, "Atención con el año");
    FrmSnapshot accented = snapshots.snapshot(PROJECT, YEAR);
    String otherSource = writer.write(accented, PackageKind.ANIO, "WE8MSWIN1252");
    String sameSource = writer.write(accented, PackageKind.ANIO, SOURCE_CHARSET);
    SqlPlusRunner sqlPlus = new SqlPlusRunner(ORACLE);
    deleteOwnRows(accented);
    Map<FrmTable, Long> before = tableCounts();

    SqlPlusRunner.Run rejected = sqlPlus.run(otherSource);
    assertThat(rejected.exitCode()).isNotZero();
    assertThat(rejected.output())
        .contains("ORA-20002")
        .contains("Juego de caracteres distinto: origen WE8MSWIN1252, destino AL32UTF8");
    assertThat(tableCounts()).isEqualTo(before);

    SqlPlusRunner.Run accepted = sqlPlus.run(sameSource);
    assertThat(accepted.exitCode()).as(accepted.output()).isZero();
    assertThat(snapshots.snapshot(PROJECT, YEAR)).isEqualTo(accented);
  }

  /** {tabla (ordinal), id} de una fila con texto. */
  private static long[] slot(FrmTable table, Map<String, Object> row) {
    return new long[] {table.ordinal(), ((BigDecimal) row.get(table.idColumn())).longValueExact()};
  }

  private boolean oracleKeepsNul(long[] slot) {
    writeText(slot, HostileTexts.WITH_NUL);
    return HostileTexts.WITH_NUL.equals(readText(slot));
  }

  private void writeText(long[] slot, String text) {
    FrmTable table = FrmTable.values()[(int) slot[0]];
    String column = table == FrmTable.PREGUNTA ? "PREGUNTA" : "TEXTO";
    jdbc.update(
        "UPDATE "
            + table.tableName()
            + " SET "
            + column
            + " = ? WHERE "
            + table.idColumn()
            + " = ?",
        text,
        slot[1]);
  }

  private String readText(long[] slot) {
    FrmTable table = FrmTable.values()[(int) slot[0]];
    String column = table == FrmTable.PREGUNTA ? "PREGUNTA" : "TEXTO";
    return jdbc.queryForObject(
        "SELECT " + column + " FROM " + table.tableName() + " WHERE " + table.idColumn() + " = ?",
        String.class,
        slot[1]);
  }

  /** Borra las filas del 2027 que no comparte con 2026, de las hijas a las madres. */
  private void deleteOwnRows(FrmSnapshot snapshot) {
    FrmSnapshot published = snapshots.snapshot(PROJECT, 2026);
    List<FrmTable> tables = new ArrayList<>(FrmSnapshot.TABLES);
    Collections.reverse(tables);
    for (FrmTable table : tables) {
      Set<Object> shared = new HashSet<>();
      published.rows().get(table).forEach(row -> shared.add(row.get(table.idColumn())));
      for (Map<String, Object> row : snapshot.rows().get(table)) {
        Object id = row.get(table.idColumn());
        if (!shared.contains(id)) {
          deleteRow(table, ((BigDecimal) id).longValueExact());
        }
      }
    }
  }

  private void deleteRow(FrmTable table, long id) {
    jdbc.update("DELETE FROM " + table.tableName() + " WHERE " + table.idColumn() + " = ?", id);
  }

  private long firstId(FrmTable table) {
    return ((BigDecimal) original.rows().get(table).get(0).get(table.idColumn())).longValueExact();
  }

  private String headerText(long header) {
    return jdbc.queryForObject(
        "SELECT ENCABEZADO FROM FRM_ENCABEZADO WHERE COD_ENC = ?", String.class, header);
  }

  private Map<FrmTable, Long> tableCounts() {
    Map<FrmTable, Long> counts = new EnumMap<>(FrmTable.class);
    for (FrmTable table : FrmSnapshot.TABLES) {
      counts.put(
          table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table.tableName(), Long.class));
    }
    return counts;
  }
}
