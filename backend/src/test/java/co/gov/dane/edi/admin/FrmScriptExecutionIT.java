package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.support.DraftYearFixture;
import co.gov.dane.edi.support.FrmScriptRunner;
import co.gov.dane.edi.support.OracleIntegrationTest;
import java.math.BigDecimal;
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
    String yearScript = writer.write(original, PackageKind.ANIO);
    String correctionScript = writer.write(original, PackageKind.CORRECCION);
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
    String correctionScript = writer.write(original, PackageKind.CORRECCION);
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
    String correctionScript = writer.write(original, PackageKind.CORRECCION);
    String yearScript = writer.write(original, PackageKind.ANIO);
    long question = DraftYearFixture.H1_GROUP_2_FIRST;
    long capProy = DraftYearFixture.H1_GROUP_2_SECOND;
    long header = firstId(FrmTable.ENCABEZADO);
    long help = firstId(FrmTable.PRE_AYUDA);
    long headerHelp = firstId(FrmTable.ENC_AYUDA);
    jdbc.update("UPDATE FRM_PREGUNTA SET PREGUNTA = 'Otro texto' WHERE COD_PRE = ?", question);
    jdbc.update("UPDATE FRM_ENCABEZADO SET ENCABEZADO = NULL WHERE COD_ENC = ?", header);
    jdbc.update("UPDATE FRM_PRE_AYUDA SET TEXTO = 'Otra ayuda' WHERE COD_AYU = ?", help);
    jdbc.update("UPDATE FRM_ENC_AYUDA SET TEXTO = 'Otra' WHERE COD_AYU = ?", headerHelp);
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
