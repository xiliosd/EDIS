package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(OutputCaptureExtension.class)
class FrmWriteRepositoryIT extends OracleIntegrationTest {

  // Todo dato ficticio de esta prueba usa ids >= 900000 para no tocar el cuestionario de
  // desarrollo.
  private static final long BASE = 900000;

  @Autowired private FrmWriteRepository repository;
  @Autowired private AdminAudit audit;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void insertFixture() {
    cleanUp();
    // Pregunta compartida: 2026 (cap 900002) y 2027 (cap 900001).
    insertQuestion(900001, "Texto compartido", 900001, 3, 7);
    insertCap(900001, 900001, "EDIN", "2027");
    insertPeriod(900001, 900001, "EDIN", 2027);
    insertCap(900002, 900001, "EDIN", "2026");
    insertPeriod(900002, 900002, "EDIN", 2026);
    // Pregunta solo de 2027.
    insertQuestion(900011, "Texto propio", 900011, 1, 1);
    insertCap(900011, 900011, "EDIN", "2027");
    insertPeriod(900011, 900011, "EDIN", 2027);
    // Pregunta de EDIN 2027 compartida con EDID 2027 (otra encuesta, mismo año).
    insertQuestion(900021, "Texto de dos encuestas", 900021, 1, 2);
    insertCap(900021, 900021, "EDIN", "2027");
    insertPeriod(900021, 900021, "EDIN", 2027);
    insertCap(900022, 900021, "EDID", "2027");
    insertPeriod(900022, 900022, "EDID", 2027);
    // Una fila de cap_proy usada por dos periodos de años distintos.
    insertQuestion(900031, "Texto de cap compartido", 900031, 1, 3);
    insertCap(900031, 900031, "EDIN", "2027");
    insertPeriod(900031, 900031, "EDIN", 2027);
    insertPeriod(900032, 900031, "EDIN", 2026);
    jdbc.update(
        "INSERT INTO FRM_ENCABEZADO (COD_ENC, COD_CAP_PROY, TIPO, ENCABEZADO, ANIO)"
            + " VALUES (900001, 900001, 'E', 'Encabezado', '2027')");
    jdbc.update(
        "INSERT INTO FRM_PRE_AYUDA (COD_AYU, COD_CAP_PROY, TEXTO)"
            + " VALUES (900001, 900001, 'Ayuda')");
  }

  @AfterEach
  void cleanUp() {
    // Primero las copias que crean las pruebas (ids MAX+1 por encima de BASE y apuntando a ellas).
    for (FrmTable table : FrmTable.values()) {
      jdbc.update(
          "DELETE FROM " + table.tableName() + " WHERE " + table.idColumn() + " >= ?", BASE);
    }
  }

  private void insertQuestion(long pre, String text, long group, int groupNumber, int order) {
    jdbc.update("INSERT INTO FRM_PREGUNTA (COD_PRE, PREGUNTA, TIPO) VALUES (?, ?, 'L')", pre, text);
    jdbc.update(
        "INSERT INTO FRM_PRE_GRUPO (COD_PRE_GRUPO, GRUPO, ORDEN, COD_PRE) VALUES (?, ?, ?, ?)",
        group,
        groupNumber,
        order,
        pre);
  }

  private void insertCap(long cap, long group, String project, String year) {
    jdbc.update(
        "INSERT INTO FRM_CAP_PROY (COD_CAP_PROY, CAPITULO, NUMERAL, COD_PRE_GRUPO, PROY, ANIO)"
            + " VALUES (?, 'ZZ1', 'Z.', ?, ?, ?)",
        cap,
        group,
        project,
        year);
  }

  private void insertPeriod(long period, long cap, String project, int year) {
    jdbc.update(
        "INSERT INTO FRM_CAP_PERIODO (ID_PERIODO, ANIO, PROY, COD_CAP_PROY, NUMERAL)"
            + " VALUES (?, ?, ?, ?, 'Z.')",
        period,
        year,
        project,
        cap);
  }

  private <T> T inTransaction(Supplier<T> action) {
    return new TransactionTemplate(transactionManager).execute(status -> action.get());
  }

  private void inTransaction(Runnable action) {
    inTransaction(
        () -> {
          action.run();
          return null;
        });
  }

  private long groupOf(long cap) {
    return jdbc.queryForObject(
        "SELECT COD_PRE_GRUPO FROM FRM_CAP_PROY WHERE COD_CAP_PROY = ?", Long.class, cap);
  }

  private String textOf(long pre) {
    return jdbc.queryForObject(
        "SELECT PREGUNTA FROM FRM_PREGUNTA WHERE COD_PRE = ?", String.class, pre);
  }

  @Test
  void copyOnWriteDuplicatesSharedRowsOnly() {
    long copied = inTransaction(() -> repository.copyQuestionForYear(900001, "EDIN", "2027"));

    assertThat(copied).isNotEqualTo(900001);
    assertThat(groupOf(900001)).isNotEqualTo(900001);
    assertThat(groupOf(900002)).isEqualTo(900001);
    Map<String, Object> newGroup =
        jdbc.queryForMap("SELECT * FROM FRM_PRE_GRUPO WHERE COD_PRE_GRUPO = ?", groupOf(900001));
    assertThat(((Number) newGroup.get("COD_PRE")).longValue()).isEqualTo(copied);
    assertThat(((Number) newGroup.get("GRUPO")).intValue()).isEqualTo(3);
    assertThat(((Number) newGroup.get("ORDEN")).intValue()).isEqualTo(7);
    assertThat(textOf(copied)).isEqualTo("Texto compartido");

    inTransaction(() -> repository.updateQuestionText(copied, "Solo 2027"));
    assertThat(textOf(900001)).isEqualTo("Texto compartido");
    assertThat(textOf(copied)).isEqualTo("Solo 2027");

    long reused = inTransaction(() -> repository.copyQuestionForYear(900011, "EDIN", "2027"));
    assertThat(reused).isEqualTo(900011);
    assertThat(groupOf(900011)).isEqualTo(900011);
  }

  @Test
  void copyOnWriteAlsoProtectsOtherSurvey() {
    long copied = inTransaction(() -> repository.copyQuestionForYear(900021, "EDIN", "2027"));

    assertThat(copied).isNotEqualTo(900021);
    assertThat(groupOf(900021)).isNotEqualTo(900021);
    assertThat(groupOf(900022)).isEqualTo(900021);
  }

  @Test
  void refusesToRepointACapProyRowUsedByAnotherYear() {
    assertThatThrownBy(
            () -> inTransaction(() -> repository.copyQuestionForYear(900031, "EDIN", "2027")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(groupOf(900031)).isEqualTo(900031);
  }

  @Test
  void copyOnWriteRollsBackWithTheTransaction() {
    assertThatThrownBy(
            () ->
                inTransaction(
                    () -> {
                      repository.copyQuestionForYear(900001, "EDIN", "2027");
                      throw new IllegalStateException("falla simulada");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(groupOf(900001)).isEqualTo(900001);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM FRM_PRE_GRUPO WHERE COD_PRE_GRUPO >= ?", Long.class, BASE))
        .isEqualTo(4L);
  }

  @Test
  void unknownCapProyIsReportedAsMissing() {
    assertThatThrownBy(
            () -> inTransaction(() -> repository.copyQuestionForYear(999999, "EDIN", "2027")))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void updatesHeaderHelpAndOrder() {
    inTransaction(
        () -> {
          repository.updateHeader(900001, "Nuevo encabezado");
          repository.updateHelp(900001, "Nueva ayuda");
          repository.updateOrder(900011, 5);
        });

    assertThat(
            jdbc.queryForObject(
                "SELECT ENCABEZADO FROM FRM_ENCABEZADO WHERE COD_ENC = 900001", String.class))
        .isEqualTo("Nuevo encabezado");
    assertThat(
            jdbc.queryForObject(
                "SELECT TEXTO FROM FRM_PRE_AYUDA WHERE COD_AYU = 900001", String.class))
        .isEqualTo("Nueva ayuda");
    assertThat(
            jdbc.queryForObject(
                "SELECT ORDEN FROM FRM_PRE_GRUPO WHERE COD_PRE_GRUPO = 900011", Integer.class))
        .isEqualTo(5);
  }

  @Test
  void updatingAMissingRowFails() {
    assertThatThrownBy(() -> inTransaction(() -> repository.updateQuestionText(999999, "x")))
        .isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> inTransaction(() -> repository.updateHeader(999999, "x")))
        .isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> inTransaction(() -> repository.updateHelp(999999, "x")))
        .isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> inTransaction(() -> repository.updateOrder(999999, 1)))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void auditLineHasNoText(CapturedOutput output) {
    String secret = "TEXTO-QUE-NO-DEBE-AUDITARSE";
    inTransaction(() -> repository.updateQuestionText(900011, secret));
    audit.record("admin1", "EDIN", 2027, "editar-texto", List.of("900011"));

    assertThat(output)
        .contains("edi.admin.audit")
        .contains("operacion=editar-texto codigos=[900011]")
        .doesNotContain(secret);
  }
}
