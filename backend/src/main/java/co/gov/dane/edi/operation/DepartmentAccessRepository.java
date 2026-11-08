package co.gov.dane.edi.operation;

import co.gov.dane.edi.config.SurveyProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DepartmentAccessRepository {

  private static final String ALL_DEPARTMENTS =
      "SELECT COD_DEPTO, NOM_DEPTO FROM GRAL_DEPARTAMENTOS ORDER BY COD_DEPTO";

  // COD_USUA es NUMBER en producción y texto en desarrollo: se une como texto en ambos casos.
  // PROY es una lista ('EDIN, EDID'): se quitan los espacios y se busca la encuesta entre comas.
  private static final String ASSIGNED_DEPARTMENTS =
      "SELECT DISTINCT D.COD_DEPTO, D.NOM_DEPTO FROM GRAL_TERRITORIAL_ACCESO A"
          + " JOIN GRAL_USUARIOS_ADM U ON TO_CHAR(U.COD_USUA) = TO_CHAR(A.COD_USUA)"
          + " JOIN GRAL_DEPARTAMENTOS D ON D.COD_DEPTO = A.COD_DEPTO"
          + " WHERE UPPER(TO_CHAR(U.COD_USUA)) = UPPER(:username) AND U.PROY LIKE :userProject"
          + " AND (',' || REPLACE(A.PROY, ' ', '') || ',') LIKE :surveyList"
          + " ORDER BY D.COD_DEPTO";

  private static final String DEPARTMENT_BY_CODE =
      "SELECT COD_DEPTO, NOM_DEPTO FROM GRAL_DEPARTAMENTOS WHERE COD_DEPTO = :code";

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;

  public DepartmentAccessRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  public List<Department> findAll() {
    return jdbc.query(ALL_DEPARTMENTS, Map.of(), (row, index) -> toDepartment(row));
  }

  public List<Department> findAssignedTo(String username) {
    return jdbc.query(
        ASSIGNED_DEPARTMENTS,
        Map.of(
            "username",
            username,
            "userProject",
            survey.code() + "%",
            "surveyList",
            "%," + survey.code() + ",%"),
        (row, index) -> toDepartment(row));
  }

  public List<Department> findByCode(int code) {
    return jdbc.query(DEPARTMENT_BY_CODE, Map.of("code", code), (row, index) -> toDepartment(row));
  }

  private static Department toDepartment(ResultSet row) throws SQLException {
    String name = row.getString("NOM_DEPTO");
    return new Department(row.getInt("COD_DEPTO"), name == null ? "" : name);
  }
}
