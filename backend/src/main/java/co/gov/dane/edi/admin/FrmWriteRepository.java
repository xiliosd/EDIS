package co.gov.dane.edi.admin;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Escrituras sobre las tablas FRM_*. Todos los métodos asumen una transacción abierta por el
 * servicio.
 *
 * <p>Contrato del llamador: toda escritura estructural de FRM_* toma el candado global {@code
 * SaveLocks.withLock("admin:frm", ...)} antes de abrir la transacción, y dentro de ella respeta el
 * orden de escritura FRM_PREGUNTA, FRM_PRE_GRUPO y filas de FRM_CAP_PROY para evitar ORA-00060
 * (interbloqueo). Un candado por año no basta: dos años pueden compartir filas.
 *
 * <p>Todo método que modifica una fila la deja solo para la pareja (encuesta, año) indicada, o
 * lanza {@link SharedRowException}. Una pareja con encuesta o año NULO se trata como "otra pareja"
 * (falla cerrada).
 */
@Repository
public class FrmWriteRepository {

  private static final String OTHER_PAIR_PERIOD =
      " AND (P.PROY IS NULL OR P.ANIO IS NULL OR P.PROY <> ? OR P.ANIO <> ?)";
  private static final String OTHER_PAIR_CAP =
      " AND (C.PROY IS NULL OR C.ANIO IS NULL OR C.PROY <> ? OR C.ANIO <> ?)";

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
   * @throws SharedRowException si el propio FRM_CAP_PROY lo usa otra pareja (repuntarlo lo
   *     cambiaría también allí)
   */
  public long copyQuestionForYear(long capProyId, String project, String yearText) {
    int year = Integer.parseInt(yearText);
    long[] current = groupAndQuestionOf(capProyId);
    long groupId = current[0];
    long questionId = current[1];

    if (!isShared(groupId, questionId, capProyId, project, year)) {
      return questionId;
    }
    requireCapProyOnlyForPair(capProyId, project, year);

    long newQuestionId = ids.next(FrmTable.PREGUNTA);
    jdbc.update(
        "INSERT INTO FRM_PREGUNTA (COD_PRE, PREGUNTA, TIPO)"
            + " SELECT ?, PREGUNTA, TIPO FROM FRM_PREGUNTA WHERE COD_PRE = ?",
        newQuestionId,
        questionId);
    copyGroup(groupId, newQuestionId, capProyId);
    return newQuestionId;
  }

  /**
   * Cambia el orden de la pregunta de este cap_proy. Si su FRM_PRE_GRUPO es alcanzable desde otra
   * pareja (encuesta, año), lo copia y repunta antes de actualizar ORDEN; nunca modifica el orden
   * de otro año.
   *
   * @throws NoSuchElementException si el cap_proy no existe
   * @throws SharedRowException si el propio FRM_CAP_PROY lo usa otra pareja
   */
  public void updateOrder(long capProyId, String project, String yearText, int order) {
    int year = Integer.parseInt(yearText);
    long[] current = groupAndQuestionOf(capProyId);
    long target = current[0];
    if (isGroupShared(current[0], capProyId, project, year)) {
      requireCapProyOnlyForPair(capProyId, project, year);
      target = copyGroup(current[0], current[1], capProyId);
    }
    requireOne(
        jdbc.update("UPDATE FRM_PRE_GRUPO SET ORDEN = ? WHERE COD_PRE_GRUPO = ?", order, target),
        "grupo " + target);
  }

  /** Texto de una pregunta ya privada: obtenga {@code codPre} de {@link #copyQuestionForYear}. */
  public void updateQuestionText(long codPre, String plainText) {
    requireOne(
        jdbc.update(
            "UPDATE FRM_PREGUNTA SET PREGUNTA = ? WHERE COD_PRE = ?",
            Objects.requireNonNull(plainText),
            codPre),
        "pregunta " + codPre);
  }

  /**
   * Cambia el encabezado solo si pertenece únicamente a esta pareja (encuesta, año): su ANIO
   * coincide y su FRM_CAP_PROY no lo usa otra pareja.
   *
   * @throws NoSuchElementException si el encabezado no existe
   * @throws SharedRowException si lo usa otro año o encuesta
   */
  public void updateHeader(long codEnc, String project, String yearText, String text) {
    Objects.requireNonNull(text);
    int year = Integer.parseInt(yearText);
    List<Object[]> rows =
        jdbc.query(
            "SELECT COD_CAP_PROY, ANIO FROM FRM_ENCABEZADO WHERE COD_ENC = ?",
            (set, rowNumber) -> new Object[] {set.getLong(1), set.getString(2)},
            codEnc);
    if (rows.isEmpty()) {
      throw new NoSuchElementException("No existe el registro de encabezado " + codEnc);
    }
    if (!yearText.equals(rows.get(0)[1])) {
      throw new SharedRowException("El encabezado " + codEnc + " pertenece a otro año");
    }
    requireCapProyOnlyForPair((Long) rows.get(0)[0], project, year);
    requireOne(
        jdbc.update("UPDATE FRM_ENCABEZADO SET ENCABEZADO = ? WHERE COD_ENC = ?", text, codEnc),
        "encabezado " + codEnc);
  }

  /**
   * Cambia la ayuda solo si su FRM_CAP_PROY pertenece únicamente a esta pareja (encuesta, año).
   *
   * @throws NoSuchElementException si la ayuda no existe
   * @throws SharedRowException si la usa otro año o encuesta
   */
  public void updateHelp(long codAyu, String project, String yearText, String text) {
    Objects.requireNonNull(text);
    int year = Integer.parseInt(yearText);
    List<Long> caps =
        jdbc.query(
            "SELECT COD_CAP_PROY FROM FRM_PRE_AYUDA WHERE COD_AYU = ?",
            (set, rowNumber) -> set.getLong(1),
            codAyu);
    if (caps.isEmpty()) {
      throw new NoSuchElementException("No existe el registro de ayuda " + codAyu);
    }
    requireCapProyOnlyForPair(caps.get(0), project, year);
    requireOne(
        jdbc.update("UPDATE FRM_PRE_AYUDA SET TEXTO = ? WHERE COD_AYU = ?", text, codAyu),
        "ayuda " + codAyu);
  }

  private long[] groupAndQuestionOf(long capProyId) {
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
    return current.get(0);
  }

  /** Copia FRM_PRE_GRUPO apuntando a {@code questionId} y repunta el cap_proy a la copia. */
  private long copyGroup(long groupId, long questionId, long capProyId) {
    long newGroupId = ids.next(FrmTable.PRE_GRUPO);
    jdbc.update(
        "INSERT INTO FRM_PRE_GRUPO (COD_PRE_GRUPO, GRUPO, ORDEN, COD_PRE)"
            + " SELECT ?, GRUPO, ORDEN, ? FROM FRM_PRE_GRUPO WHERE COD_PRE_GRUPO = ?",
        newGroupId,
        questionId,
        groupId);
    jdbc.update(
        "UPDATE FRM_CAP_PROY SET COD_PRE_GRUPO = ? WHERE COD_CAP_PROY = ?", newGroupId, capProyId);
    return newGroupId;
  }

  /**
   * ¿Otra pareja (encuesta, año) usa el mismo grupo o la misma pregunta? Falla cerrada con NULL.
   */
  private boolean isShared(
      long groupId, long questionId, long capProyId, String project, int year) {
    return count(
                "SELECT COUNT(*) FROM FRM_CAP_PERIODO P"
                    + " JOIN FRM_CAP_PROY C ON C.COD_CAP_PROY = P.COD_CAP_PROY"
                    + " JOIN FRM_PRE_GRUPO G ON G.COD_PRE_GRUPO = C.COD_PRE_GRUPO"
                    + " WHERE (C.COD_PRE_GRUPO = ? OR G.COD_PRE = ?)"
                    + OTHER_PAIR_PERIOD,
                groupId,
                questionId,
                project,
                year)
            > 0
        || count(
                "SELECT COUNT(*) FROM FRM_CAP_PROY C"
                    + " JOIN FRM_PRE_GRUPO G ON G.COD_PRE_GRUPO = C.COD_PRE_GRUPO"
                    + " WHERE (C.COD_PRE_GRUPO = ? OR G.COD_PRE = ?) AND C.COD_CAP_PROY <> ?"
                    + OTHER_PAIR_CAP,
                groupId,
                questionId,
                capProyId,
                project,
                String.valueOf(year))
            > 0;
  }

  private boolean isGroupShared(long groupId, long capProyId, String project, int year) {
    return count(
                "SELECT COUNT(*) FROM FRM_CAP_PERIODO P"
                    + " JOIN FRM_CAP_PROY C ON C.COD_CAP_PROY = P.COD_CAP_PROY"
                    + " WHERE C.COD_PRE_GRUPO = ?"
                    + OTHER_PAIR_PERIOD,
                groupId,
                project,
                year)
            > 0
        || count(
                "SELECT COUNT(*) FROM FRM_CAP_PROY C WHERE C.COD_PRE_GRUPO = ?"
                    + " AND C.COD_CAP_PROY <> ?"
                    + OTHER_PAIR_CAP,
                groupId,
                capProyId,
                project,
                String.valueOf(year))
            > 0;
  }

  /** Falla con {@link SharedRowException} si el cap_proy lo usa otra pareja. */
  private void requireCapProyOnlyForPair(long capProyId, String project, int year) {
    long byPeriods =
        count(
            "SELECT COUNT(*) FROM FRM_CAP_PERIODO P WHERE P.COD_CAP_PROY = ?" + OTHER_PAIR_PERIOD,
            capProyId,
            project,
            year);
    long byOwnColumns =
        count(
            "SELECT COUNT(*) FROM FRM_CAP_PROY C WHERE C.COD_CAP_PROY = ?" + OTHER_PAIR_CAP,
            capProyId,
            project,
            String.valueOf(year));
    if (byPeriods > 0 || byOwnColumns > 0) {
      throw new SharedRowException("La fila " + capProyId + " de FRM_CAP_PROY la usa otro año");
    }
  }

  private long count(String sql, Object... arguments) {
    Long value = jdbc.queryForObject(sql, Long.class, arguments);
    return value == null ? 0 : value;
  }

  private static void requireOne(int affectedRows, String description) {
    if (affectedRows != 1) {
      throw new NoSuchElementException("No existe el registro de " + description);
    }
  }
}
