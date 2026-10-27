package co.gov.dane.edi.admin;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Escrituras sobre las tablas FRM_*. Todos los métodos asumen una transacción abierta por el
 * servicio (que también toma el candado del año): la copia al escribir y la asignación de ids deben
 * confirmarse o deshacerse juntas.
 */
@Repository
public class FrmWriteRepository {

  private final JdbcTemplate jdbc;
  private final FrmIdAllocator ids;

  public FrmWriteRepository(JdbcTemplate jdbc, FrmIdAllocator ids) {
    this.jdbc = jdbc;
    this.ids = ids;
  }

  /**
   * Copia al escribir: si la pregunta del cap_proy la usa también otra pareja (encuesta, año), se
   * copian FRM_PREGUNTA y FRM_PRE_GRUPO y se repunta solo este FRM_CAP_PROY. Devuelve el COD_PRE
   * que se puede editar sin afectar a los demás años.
   *
   * @throws NoSuchElementException si el cap_proy no existe
   * @throws IllegalStateException si el propio FRM_CAP_PROY lo usa otro año (repuntarlo lo
   *     cambiaría también allí; los cap_proy se crean por año, así que no debería ocurrir)
   */
  public long copyQuestionForYear(long capProyId, String project, String yearText) {
    int year = Integer.parseInt(yearText);
    List<long[]> current =
        jdbc.query(
            "SELECT C.COD_PRE_GRUPO, G.COD_PRE FROM FRM_CAP_PROY C"
                + " JOIN FRM_PRE_GRUPO G ON G.COD_PRE_GRUPO = C.COD_PRE_GRUPO"
                + " WHERE C.COD_CAP_PROY = ?",
            (rows, rowNumber) -> new long[] {rows.getLong(1), rows.getLong(2)},
            capProyId);
    if (current.isEmpty()) {
      throw new NoSuchElementException("No existe la pregunta " + capProyId);
    }
    long groupId = current.get(0)[0];
    long questionId = current.get(0)[1];

    if (!isSharedWithOtherPair(groupId, questionId, capProyId, project, year)) {
      return questionId;
    }
    if (capProyUsedByOtherPair(capProyId, project, year)) {
      throw new IllegalStateException(
          "La fila " + capProyId + " de FRM_CAP_PROY es usada por otro año; no se puede repuntar");
    }

    long newQuestionId = ids.next(FrmTable.PREGUNTA);
    jdbc.update(
        "INSERT INTO FRM_PREGUNTA (COD_PRE, PREGUNTA, TIPO)"
            + " SELECT ?, PREGUNTA, TIPO FROM FRM_PREGUNTA WHERE COD_PRE = ?",
        newQuestionId,
        questionId);
    long newGroupId = ids.next(FrmTable.PRE_GRUPO);
    jdbc.update(
        "INSERT INTO FRM_PRE_GRUPO (COD_PRE_GRUPO, GRUPO, ORDEN, COD_PRE)"
            + " SELECT ?, GRUPO, ORDEN, ? FROM FRM_PRE_GRUPO WHERE COD_PRE_GRUPO = ?",
        newGroupId,
        newQuestionId,
        groupId);
    jdbc.update(
        "UPDATE FRM_CAP_PROY SET COD_PRE_GRUPO = ? WHERE COD_CAP_PROY = ?", newGroupId, capProyId);
    return newQuestionId;
  }

  /** ¿Otra pareja (encuesta, año) usa el mismo grupo o la misma pregunta? */
  private boolean isSharedWithOtherPair(
      long groupId, long questionId, long capProyId, String project, int year) {
    Long throughPeriods =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM FRM_CAP_PERIODO P"
                + " JOIN FRM_CAP_PROY C ON C.COD_CAP_PROY = P.COD_CAP_PROY"
                + " JOIN FRM_PRE_GRUPO G ON G.COD_PRE_GRUPO = C.COD_PRE_GRUPO"
                + " WHERE (C.COD_PRE_GRUPO = ? OR G.COD_PRE = ?)"
                + " AND NOT (P.PROY = ? AND P.ANIO = ?)",
            Long.class,
            groupId,
            questionId,
            project,
            year);
    Long throughCapProy =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM FRM_CAP_PROY C"
                + " JOIN FRM_PRE_GRUPO G ON G.COD_PRE_GRUPO = C.COD_PRE_GRUPO"
                + " WHERE (C.COD_PRE_GRUPO = ? OR G.COD_PRE = ?) AND C.COD_CAP_PROY <> ?"
                + " AND NOT (C.PROY = ? AND C.ANIO = ?)",
            Long.class,
            groupId,
            questionId,
            capProyId,
            project,
            String.valueOf(year));
    return (throughPeriods != null && throughPeriods > 0)
        || (throughCapProy != null && throughCapProy > 0);
  }

  private boolean capProyUsedByOtherPair(long capProyId, String project, int year) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM FRM_CAP_PERIODO"
                + " WHERE COD_CAP_PROY = ? AND NOT (PROY = ? AND ANIO = ?)",
            Long.class,
            capProyId,
            project,
            year);
    return count != null && count > 0;
  }

  public void updateQuestionText(long codPre, String plainText) {
    requireOne(
        jdbc.update(
            "UPDATE FRM_PREGUNTA SET PREGUNTA = ? WHERE COD_PRE = ?",
            Objects.requireNonNull(plainText),
            codPre),
        "pregunta " + codPre);
  }

  public void updateHeader(long codEnc, String text) {
    requireOne(
        jdbc.update(
            "UPDATE FRM_ENCABEZADO SET ENCABEZADO = ? WHERE COD_ENC = ?",
            Objects.requireNonNull(text),
            codEnc),
        "encabezado " + codEnc);
  }

  public void updateHelp(long codAyu, String text) {
    requireOne(
        jdbc.update(
            "UPDATE FRM_PRE_AYUDA SET TEXTO = ? WHERE COD_AYU = ?",
            Objects.requireNonNull(text),
            codAyu),
        "ayuda " + codAyu);
  }

  public void updateOrder(long codPreGrupo, int order) {
    requireOne(
        jdbc.update(
            "UPDATE FRM_PRE_GRUPO SET ORDEN = ? WHERE COD_PRE_GRUPO = ?", order, codPreGrupo),
        "grupo " + codPreGrupo);
  }

  private static void requireOne(int affectedRows, String description) {
    if (affectedRows != 1) {
      throw new NoSuchElementException("No existe el registro de " + description);
    }
  }
}
