package co.gov.dane.edi.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Copia en FRM_* del cuestionario de un año en otro. Asume una transacción abierta por el servicio
 * y el candado global {@code admin:frm} (ver {@link FrmWriteRepository}).
 *
 * <p>Cada pregunta del año nuevo recibe su propio FRM_CAP_PROY (con su periodo, objeto, encabezados
 * y ayudas): así editar el año nuevo nunca choca con una fila de otro año. FRM_PRE_GRUPO y
 * FRM_PREGUNTA siguen compartidos y la copia al escribir los separa cuando se editan. No se copia
 * FRM_SALTO: sus saltos pasan al archivo de reglas del año nuevo.
 */
@Repository
public class YearCopyRepository {

  /** Ids consecutivos {@code first .. first + count - 1} insertados en una tabla. */
  public record CopiedRange(FrmTable table, long first, int count) {}

  /** Lo insertado por una copia, en el orden de inserción. */
  public record CopiedYear(List<CopiedRange> ranges) {

    public CopiedYear {
      ranges = List.copyOf(ranges);
    }
  }

  private static final String SOURCE_CAP_PROYS =
      " IN (SELECT P.COD_CAP_PROY FROM FRM_CAP_PERIODO P WHERE P.PROY = ? AND P.ANIO = ?)";

  private final JdbcTemplate jdbc;
  private final FrmIdAllocator ids;

  public YearCopyRepository(JdbcTemplate jdbc, FrmIdAllocator ids) {
    this.jdbc = jdbc;
    this.ids = ids;
  }

  /** Filas de FRM_CAP_PERIODO de la encuesta y año: cero si el año no tiene preguntas. */
  public long periodCount(String project, int year) {
    Long found =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM FRM_CAP_PERIODO WHERE PROY = ? AND ANIO = ?",
            Long.class,
            project,
            year);
    return found == null ? 0 : found;
  }

  /**
   * Copia las preguntas de {@code fromYear} en {@code year}. Las tablas se escriben en el orden de
   * bloqueo documentado: FRM_CAP_PROY, FRM_ENCABEZADO, FRM_PRE_AYUDA, FRM_CAP_PERIODO, FRM_OBJ_PRE
   * y FRM_ENC_AYUDA.
   */
  public CopiedYear copy(String project, int fromYear, int year) {
    List<CopiedRange> copied = new ArrayList<>();
    Map<Long, Long> capProys = copyCapProys(project, fromYear, year, copied);
    Map<Long, Long> headers = copyHeaders(project, fromYear, year, capProys, copied);
    copyHelp(project, fromYear, capProys, copied);
    copyPeriods(project, fromYear, year, capProys, copied);
    copyObjects(project, fromYear, capProys, copied);
    copyHeaderHelp(project, fromYear, headers, copied);
    return new CopiedYear(copied);
  }

  /** Deshace una copia ya confirmada: borra primero las hijas por si el esquema real tiene FK. */
  public void delete(CopiedYear copied) {
    List<CopiedRange> ranges = new ArrayList<>(copied.ranges());
    Collections.reverse(ranges);
    for (CopiedRange range : ranges) {
      FrmTable table = range.table();
      jdbc.update(
          "DELETE FROM " + table.tableName() + " WHERE " + table.idColumn() + " BETWEEN ? AND ?",
          range.first(),
          range.first() + range.count() - 1);
    }
  }

  /** Un FRM_CAP_PROY nuevo por cada pregunta con periodo en el año de origen. */
  Map<Long, Long> copyCapProys(String project, int fromYear, int year, List<CopiedRange> copied) {
    Map<Long, Object[]> sources = new LinkedHashMap<>();
    jdbc.query(
        "SELECT C.COD_CAP_PROY, C.CAPITULO, C.NUMERAL, C.COD_PRE_GRUPO FROM FRM_CAP_PROY C"
            + " WHERE C.COD_CAP_PROY"
            + SOURCE_CAP_PROYS
            + " ORDER BY C.COD_CAP_PROY",
        rows -> {
          sources.put(
              rows.getLong(1),
              new Object[] {rows.getString(2), rows.getString(3), rows.getObject(4)});
        },
        project,
        fromYear);
    Map<Long, Long> mapping = new LinkedHashMap<>();
    List<Object[]> inserts = new ArrayList<>();
    long next = allocate(FrmTable.CAP_PROY, sources.size(), copied);
    for (Map.Entry<Long, Object[]> source : sources.entrySet()) {
      long id = next++;
      mapping.put(source.getKey(), id);
      Object[] values = source.getValue();
      inserts.add(
          new Object[] {id, values[0], values[1], values[2], project, String.valueOf(year)});
    }
    batch(
        "INSERT INTO FRM_CAP_PROY (COD_CAP_PROY, CAPITULO, NUMERAL, COD_PRE_GRUPO, PROY, ANIO)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        inserts);
    return mapping;
  }

  /** Encabezados del año de origen (los de otros años de la misma pregunta no se copian). */
  Map<Long, Long> copyHeaders(
      String project, int fromYear, int year, Map<Long, Long> capProys, List<CopiedRange> copied) {
    List<Object[]> sources =
        jdbc.query(
            "SELECT E.COD_ENC, E.COD_CAP_PROY, E.TIPO, E.ENCABEZADO FROM FRM_ENCABEZADO E"
                + " WHERE E.ANIO = ? AND E.COD_CAP_PROY"
                + SOURCE_CAP_PROYS
                + " ORDER BY E.COD_ENC",
            (rows, rowNumber) ->
                new Object[] {
                  rows.getLong(1), rows.getLong(2), rows.getString(3), rows.getString(4)
                },
            String.valueOf(fromYear),
            project,
            fromYear);
    Map<Long, Long> mapping = new LinkedHashMap<>();
    List<Object[]> inserts = new ArrayList<>();
    long next = allocate(FrmTable.ENCABEZADO, sources.size(), copied);
    for (Object[] source : sources) {
      long id = next++;
      mapping.put((Long) source[0], id);
      inserts.add(
          new Object[] {
            id, capProys.get((Long) source[1]), source[2], source[3], String.valueOf(year)
          });
    }
    batch(
        "INSERT INTO FRM_ENCABEZADO (COD_ENC, COD_CAP_PROY, TIPO, ENCABEZADO, ANIO)"
            + " VALUES (?, ?, ?, ?, ?)",
        inserts);
    return mapping;
  }

  void copyHelp(String project, int fromYear, Map<Long, Long> capProys, List<CopiedRange> copied) {
    List<Object[]> sources =
        jdbc.query(
            "SELECT A.COD_CAP_PROY, A.TEXTO FROM FRM_PRE_AYUDA A WHERE A.COD_CAP_PROY"
                + SOURCE_CAP_PROYS
                + " ORDER BY A.COD_AYU",
            (rows, rowNumber) -> new Object[] {rows.getLong(1), rows.getString(2)},
            project,
            fromYear);
    List<Object[]> inserts = new ArrayList<>();
    long next = allocate(FrmTable.PRE_AYUDA, sources.size(), copied);
    for (Object[] source : sources) {
      inserts.add(new Object[] {next++, capProys.get((Long) source[0]), source[1]});
    }
    batch("INSERT INTO FRM_PRE_AYUDA (COD_AYU, COD_CAP_PROY, TEXTO) VALUES (?, ?, ?)", inserts);
  }

  void copyPeriods(
      String project, int fromYear, int year, Map<Long, Long> capProys, List<CopiedRange> copied) {
    List<Object[]> sources =
        jdbc.query(
            "SELECT P.COD_CAP_PROY, P.NUMERAL FROM FRM_CAP_PERIODO P"
                + " WHERE P.PROY = ? AND P.ANIO = ? ORDER BY P.ID_PERIODO",
            (rows, rowNumber) -> new Object[] {rows.getLong(1), rows.getString(2)},
            project,
            fromYear);
    List<Object[]> inserts = new ArrayList<>();
    long next = allocate(FrmTable.CAP_PERIODO, sources.size(), copied);
    for (Object[] source : sources) {
      inserts.add(new Object[] {next++, year, project, capProys.get((Long) source[0]), source[1]});
    }
    batch(
        "INSERT INTO FRM_CAP_PERIODO (ID_PERIODO, ANIO, PROY, COD_CAP_PROY, NUMERAL)"
            + " VALUES (?, ?, ?, ?, ?)",
        inserts);
  }

  void copyObjects(
      String project, int fromYear, Map<Long, Long> capProys, List<CopiedRange> copied) {
    List<Object[]> sources =
        jdbc.query(
            "SELECT O.COD_CAP_PROY, O.COD_GRUPO, O.GRUPO, O.COD_OBJ FROM FRM_OBJ_PRE O"
                + " WHERE O.COD_CAP_PROY"
                + SOURCE_CAP_PROYS
                + " ORDER BY O.COD_OBJ_PRE",
            (rows, rowNumber) ->
                new Object[] {
                  rows.getLong(1), rows.getObject(2), rows.getObject(3), rows.getObject(4)
                },
            project,
            fromYear);
    List<Object[]> inserts = new ArrayList<>();
    long next = allocate(FrmTable.OBJ_PRE, sources.size(), copied);
    for (Object[] source : sources) {
      inserts.add(
          new Object[] {next++, source[1], source[2], source[3], capProys.get((Long) source[0])});
    }
    batch(
        "INSERT INTO FRM_OBJ_PRE (COD_OBJ_PRE, COD_GRUPO, GRUPO, COD_OBJ, COD_CAP_PROY)"
            + " VALUES (?, ?, ?, ?, ?)",
        inserts);
  }

  void copyHeaderHelp(
      String project, int fromYear, Map<Long, Long> headers, List<CopiedRange> copied) {
    List<Object[]> sources =
        jdbc.query(
            "SELECT A.COD_ENC, A.TEXTO FROM FRM_ENC_AYUDA A"
                + " JOIN FRM_ENCABEZADO E ON E.COD_ENC = A.COD_ENC"
                + " WHERE E.ANIO = ? AND E.COD_CAP_PROY"
                + SOURCE_CAP_PROYS
                + " ORDER BY A.COD_AYU",
            (rows, rowNumber) -> new Object[] {rows.getLong(1), rows.getString(2)},
            String.valueOf(fromYear),
            project,
            fromYear);
    List<Object[]> inserts = new ArrayList<>();
    long next = allocate(FrmTable.ENC_AYUDA, sources.size(), copied);
    for (Object[] source : sources) {
      inserts.add(new Object[] {next++, headers.get((Long) source[0]), source[1]});
    }
    batch("INSERT INTO FRM_ENC_AYUDA (COD_AYU, COD_ENC, TEXTO) VALUES (?, ?, ?)", inserts);
  }

  /**
   * Primer id de un bloque de {@code count} filas: el bloqueo de la tabla dura hasta el fin de la
   * transacción, así los ids siguientes al MAX+1 siguen libres. Sin filas no se bloquea la tabla.
   */
  private long allocate(FrmTable table, int count, List<CopiedRange> copied) {
    if (count == 0) {
      return 0;
    }
    long first = ids.next(table);
    copied.add(new CopiedRange(table, first, count));
    return first;
  }

  private void batch(String sql, List<Object[]> rows) {
    if (!rows.isEmpty()) {
      jdbc.batchUpdate(sql, rows);
    }
  }
}
