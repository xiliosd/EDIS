package co.gov.dane.edi.admin;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lee, sin escribir nada, la fotografía FRM_* de un año. La clausura es la misma de {@link
 * YearCopyRepository}: los cap_proy con periodo en la encuesta y año, sus grupos y preguntas, y sus
 * encabezados (solo los del año), ayudas, objetos y ayudas de encabezado. Todo valor va como
 * parámetro; las tablas y columnas salen de {@link FrmTable}.
 */
@Repository
public class FrmSnapshotRepository {

  private static final String CAP_PROYS =
      "SELECT P.COD_CAP_PROY FROM FRM_CAP_PERIODO P WHERE P.PROY = ? AND P.ANIO = ?";
  private static final String GROUPS =
      "SELECT C.COD_PRE_GRUPO FROM FRM_CAP_PROY C WHERE C.COD_CAP_PROY IN (" + CAP_PROYS + ")";
  private static final String HEADERS =
      "SELECT E.COD_ENC FROM FRM_ENCABEZADO E WHERE E.ANIO = ? AND E.COD_CAP_PROY IN ("
          + CAP_PROYS
          + ")";

  private final JdbcTemplate jdbc;

  public FrmSnapshotRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public FrmSnapshot snapshot(String project, int year) {
    String yearText = String.valueOf(year);
    Map<FrmTable, List<Map<String, Object>>> rows = new EnumMap<>(FrmTable.class);
    rows.put(
        FrmTable.PREGUNTA,
        read(
            FrmTable.PREGUNTA,
            "COD_PRE IN (SELECT G.COD_PRE FROM FRM_PRE_GRUPO G WHERE G.COD_PRE_GRUPO IN ("
                + GROUPS
                + "))",
            project,
            year));
    rows.put(
        FrmTable.PRE_GRUPO,
        read(FrmTable.PRE_GRUPO, "COD_PRE_GRUPO IN (" + GROUPS + ")", project, year));
    rows.put(
        FrmTable.CAP_PROY,
        read(FrmTable.CAP_PROY, "COD_CAP_PROY IN (" + CAP_PROYS + ")", project, year));
    rows.put(
        FrmTable.CAP_PERIODO, read(FrmTable.CAP_PERIODO, "PROY = ? AND ANIO = ?", project, year));
    rows.put(
        FrmTable.ENCABEZADO,
        read(
            FrmTable.ENCABEZADO,
            "ANIO = ? AND COD_CAP_PROY IN (" + CAP_PROYS + ")",
            yearText,
            project,
            year));
    rows.put(
        FrmTable.PRE_AYUDA,
        read(FrmTable.PRE_AYUDA, "COD_CAP_PROY IN (" + CAP_PROYS + ")", project, year));
    rows.put(
        FrmTable.OBJ_PRE,
        read(FrmTable.OBJ_PRE, "COD_CAP_PROY IN (" + CAP_PROYS + ")", project, year));
    rows.put(
        FrmTable.ENC_AYUDA,
        read(FrmTable.ENC_AYUDA, "COD_ENC IN (" + HEADERS + ")", yearText, project, year));
    return new FrmSnapshot(project, year, rows);
  }

  private List<Map<String, Object>> read(FrmTable table, String where, Object... parameters) {
    List<String> columns = table.columns();
    String sql =
        "SELECT "
            + String.join(", ", columns)
            + " FROM "
            + table.tableName()
            + " WHERE "
            + where
            + " ORDER BY "
            + table.idColumn();
    return jdbc.query(sql, (resultSet, rowNumber) -> row(columns, resultSet), parameters);
  }

  /** De los COD_AYU indicados, los que no tienen definición en GRAL_AYUDAS de esta base. */
  public List<Long> missingHelpDefinitions(List<Long> codes) {
    List<Long> missing = new ArrayList<>();
    for (Long code : codes) {
      Long found =
          jdbc.queryForObject("SELECT COUNT(*) FROM GRAL_AYUDAS WHERE COD = ?", Long.class, code);
      if (found == null || found == 0) {
        missing.add(code);
      }
    }
    return missing;
  }

  /** NLS_CHARACTERSET de esta base: el paquete lo anota y la importación lo compara. */
  public String characterSet() {
    return jdbc.queryForObject(
        "SELECT VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER = 'NLS_CHARACTERSET'",
        String.class);
  }

  private static Map<String, Object> row(List<String> columns, ResultSet resultSet)
      throws SQLException {
    Map<String, Object> row = new LinkedHashMap<>();
    for (int index = 0; index < columns.size(); index++) {
      row.put(columns.get(index), value(resultSet.getObject(index + 1)));
    }
    return row;
  }

  private static Object value(Object raw) throws SQLException {
    return switch (raw) {
      case null -> null;
      case String text -> text;
      case BigDecimal number -> number;
      case Number number -> new BigDecimal(number.toString());
      default ->
          throw new SQLException(
              "Tipo de columna no admitido en FRM_*: " + raw.getClass().getSimpleName());
    };
  }
}
