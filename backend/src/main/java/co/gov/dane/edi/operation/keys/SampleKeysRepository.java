package co.gov.dane.edi.operation.keys;

import co.gov.dane.edi.config.SurveyProperties;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Usuarios y claves de la muestra. Única escritura: {@code LGN} y {@code PSW} de EDIN_MUESTRA, y
 * solo donde {@code LGN} es nulo (nunca sobrescribe). Las claves están en texto plano por la deuda
 * D7 (sin cambio de esquema). La tabla sale de {@link SurveyProperties#sampleTableName()}; todo
 * valor va como parámetro.
 */
@Repository
public class SampleKeysRepository {

  /**
   * Quién puede recibir clave: el mismo criterio del login del encuestado (carga web y activo). Las
   * cuatro consultas lo comparten para que el resumen, la generación y la descarga cuadren.
   */
  private static final String ELIGIBLE = " m.ANIO = :year AND m.TC1 = 1 AND m.ATV = 1";

  /** Elegibles por departamento (código 0 si la muestra no lo tiene). */
  public record DepartmentRow(int code, String name, long eligible, long withKey) {}

  /**
   * Fila pendiente bloqueada: su ROWID distingue filas con la misma cédula en el año. {@code
   * toString} no muestra la cédula.
   */
  public record PendingRow(String rowId, String respondentCode) {
    @Override
    public String toString() {
      return "PendingRow[rowId=" + rowId + ", cedula=oculta]";
    }
  }

  /** Usuario y clave nuevos para una fila pendiente. {@code toString} no muestra la clave. */
  public record Assignment(PendingRow row, String login, String password) {
    @Override
    public String toString() {
      return "Assignment[oculto]";
    }
  }

  /** Fila de la descarga por departamento. {@code toString} no muestra datos personales. */
  public record CredentialRow(
      String entityName,
      long entityCode,
      String respondentCode,
      String name,
      String login,
      String password) {
    @Override
    public String toString() {
      return "CredentialRow[entidad=" + entityCode + "]";
    }
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;

  public SampleKeysRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  /** Elegibles y con usuario por departamento, en una sola consulta agregada. */
  public List<DepartmentRow> findDepartments(int year) {
    String sql =
        "SELECT TO_NUMBER(m.COD_DEPTO) AS DEPARTMENT_CODE, MAX(d.NOM_DEPTO) AS DEPARTMENT_NAME,"
            + " COUNT(*) AS ELIGIBLE, COUNT(m.LGN) AS WITH_KEY FROM "
            + survey.sampleTableName()
            + " m LEFT JOIN GRAL_DEPARTAMENTOS d ON d.COD_DEPTO = TO_NUMBER(m.COD_DEPTO)"
            + " WHERE"
            + ELIGIBLE
            + " GROUP BY TO_NUMBER(m.COD_DEPTO) ORDER BY TO_NUMBER(m.COD_DEPTO) NULLS FIRST";
    return jdbc.query(
        sql,
        Map.of("year", String.valueOf(year)),
        (row, index) ->
            new DepartmentRow(
                row.getInt("DEPARTMENT_CODE"),
                row.getString("DEPARTMENT_NAME"),
                row.getLong("ELIGIBLE"),
                row.getLong("WITH_KEY")));
  }

  /**
   * Elegibles sin usuario, bloqueados hasta el fin de la transacción para que nadie les asigne otro
   * entretanto.
   */
  public List<PendingRow> findPendingForUpdate(int year) {
    String sql =
        "SELECT ROWIDTOCHAR(m.ROWID) AS ROW_ID, m.C_C FROM "
            + survey.sampleTableName()
            + " m WHERE"
            + ELIGIBLE
            + " AND m.LGN IS NULL FOR UPDATE";
    return jdbc.query(
        sql,
        Map.of("year", String.valueOf(year)),
        (row, index) -> new PendingRow(row.getString("ROW_ID"), row.getString("C_C")));
  }

  /** Todos los usuarios ya asignados en el año (de cualquier funcionario), para no repetirlos. */
  public Set<String> findLoginsOfYear(int year) {
    Set<String> logins = new HashSet<>();
    jdbc.query(
        "SELECT LGN FROM " + survey.sampleTableName() + " WHERE ANIO = :year AND LGN IS NOT NULL",
        Map.of("year", String.valueOf(year)),
        row -> {
          logins.add(row.getString("LGN"));
        });
    return logins;
  }

  /**
   * Asigna usuario y clave fila por fila, solo si sigue sin usuario.
   *
   * @return filas actualizadas por cada asignación, en el mismo orden
   */
  public int[] assign(int year, List<Assignment> assignments) {
    String sql =
        "UPDATE "
            + survey.sampleTableName()
            + " SET LGN = :login, PSW = :password"
            + " WHERE ROWID = CHARTOROWID(:rowId) AND C_C = :respondentCode AND ANIO = :year"
            + " AND LGN IS NULL";
    SqlParameterSource[] batch = new SqlParameterSource[assignments.size()];
    for (int index = 0; index < assignments.size(); index++) {
      Assignment assignment = assignments.get(index);
      batch[index] =
          new MapSqlParameterSource()
              .addValue("login", assignment.login(), Types.VARCHAR)
              .addValue("password", assignment.password(), Types.VARCHAR)
              .addValue("rowId", assignment.row().rowId(), Types.VARCHAR)
              .addValue("respondentCode", assignment.row().respondentCode(), Types.VARCHAR)
              .addValue("year", String.valueOf(year), Types.VARCHAR);
    }
    return jdbc.batchUpdate(sql, batch);
  }

  /** Si el departamento tiene funcionarios elegibles en la muestra del año. */
  public boolean hasEligible(int year, int departmentCode) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM "
                + survey.sampleTableName()
                + " m WHERE"
                + ELIGIBLE
                + " AND TO_NUMBER(m.COD_DEPTO) = :department",
            Map.of("year", String.valueOf(year), "department", departmentCode),
            Long.class);
    return count != null && count > 0;
  }

  /**
   * Elegibles con usuario del departamento, para la carta al coordinador. GRAL_ENTI se agrega por
   * código para que un código repetido en el catálogo no duplique funcionarios.
   */
  public List<CredentialRow> findCredentials(int year, int departmentCode) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("year", String.valueOf(year));
    parameters.put("department", departmentCode);
    String sql =
        "SELECT ent.ENTIDAD AS ENTITY_NAME, TO_NUMBER(m.COD_ENT) AS ENTITY_CODE, m.C_C,"
            + " m.NOM_APE, m.LGN, m.PSW FROM "
            + survey.sampleTableName()
            + " m LEFT JOIN (SELECT COD_ENT, MAX(ENTIDAD) AS ENTIDAD FROM GRAL_ENTI GROUP BY"
            + " COD_ENT) ent ON ent.COD_ENT = TO_NUMBER(m.COD_ENT)"
            + " WHERE"
            + ELIGIBLE
            + " AND m.LGN IS NOT NULL AND TO_NUMBER(m.COD_DEPTO) = :department"
            + " ORDER BY ent.ENTIDAD NULLS LAST, TO_NUMBER(m.COD_ENT), m.NOM_APE, m.C_C";
    return jdbc.query(
        sql,
        parameters,
        (row, index) -> {
          long entityCode = row.getLong("ENTITY_CODE");
          String entityName = row.getString("ENTITY_NAME");
          return new CredentialRow(
              entityName == null || entityName.isBlank() ? "Entidad " + entityCode : entityName,
              entityCode,
              row.getString("C_C"),
              row.getString("NOM_APE"),
              row.getString("LGN"),
              row.getString("PSW"));
        });
  }
}
