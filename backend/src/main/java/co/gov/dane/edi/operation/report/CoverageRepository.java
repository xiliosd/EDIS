package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.config.SurveyProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Una sola consulta agrupada sobre la muestra con el estado de cada funcionario (criterio único,
 * {@link SampleStatus#STATUS_CASE}). Los nombres de tabla llegan ya validados por {@code
 * AnswerTableResolver} y {@code SurveyProperties}; todo valor va como parámetro.
 */
@Repository
public class CoverageRepository {

  /** Una fila agrupada: departamento, entidad, nivel, carga (TC1), estado y cuántos son. */
  public record CoverageRow(
      int departmentCode,
      String departmentName,
      long entityCode,
      String entityName,
      Integer level,
      Integer channel,
      SampleStatus status,
      long total) {}

  private static final String COLUMN_EXISTS =
      "SELECT COUNT(*) FROM ALL_TAB_COLUMNS"
          + " WHERE OWNER = :owner AND TABLE_NAME = :tableName AND COLUMN_NAME = :columnName";

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;

  public CoverageRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  public boolean hasColumn(String table, String column) {
    Integer found =
        jdbc.queryForObject(
            COLUMN_EXISTS,
            Map.of("owner", survey.dbSchema(), "tableName", table, "columnName", column),
            Integer.class);
    return found != null && found > 0;
  }

  /**
   * @param departments departamentos a incluir; nulo para no filtrar por departamento
   */
  public List<CoverageRow> findRows(
      String chapterBTable,
      String chapterHTable,
      int year,
      ReportSurvey reportSurvey,
      List<Integer> departments) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("year", String.valueOf(year));
    StringBuilder sql =
        new StringBuilder(
            "SELECT d.COD_DEPTO AS DEPARTMENT_CODE, d.NOM_DEPTO AS DEPARTMENT_NAME,"
                + " TO_NUMBER(m.COD_ENT) AS ENTITY_CODE, e.ENTIDAD AS ENTITY_NAME,"
                + " m.AREA_TRA AS LEVEL_CODE, m.TC1 AS CHANNEL, ");
    sql.append(SampleStatus.STATUS_CASE).append(" AS STATUS, COUNT(*) AS TOTAL FROM ");
    sql.append(survey.sampleTableName()).append(" m");
    sql.append(" LEFT JOIN ").append(chapterBTable).append(" b ON b.C_C = m.C_C");
    sql.append(" LEFT JOIN ").append(chapterHTable).append(" h ON h.C_C = m.C_C");
    sql.append(" JOIN GRAL_DEPARTAMENTOS d ON d.COD_DEPTO = TO_NUMBER(m.COD_DEPTO)");
    sql.append(" LEFT JOIN GRAL_ENTI e ON e.COD_ENT = TO_NUMBER(m.COD_ENT)");
    sql.append(" WHERE m.ANIO = :year AND m.ATV = 1");
    if (reportSurvey.encCode() != null) {
      sql.append(" AND m.ENC = :enc");
      parameters.put("enc", reportSurvey.encCode());
    }
    if (departments != null) {
      sql.append(" AND d.COD_DEPTO IN (:departments)");
      parameters.put("departments", departments);
    }
    sql.append(
        " GROUP BY d.COD_DEPTO, d.NOM_DEPTO, TO_NUMBER(m.COD_ENT), e.ENTIDAD, m.AREA_TRA, m.TC1, ");
    sql.append(SampleStatus.STATUS_CASE);
    return jdbc.query(
        sql.toString(),
        parameters,
        (row, index) ->
            new CoverageRow(
                row.getInt("DEPARTMENT_CODE"),
                row.getString("DEPARTMENT_NAME"),
                row.getLong("ENTITY_CODE"),
                row.getString("ENTITY_NAME"),
                row.getObject("LEVEL_CODE", Integer.class),
                row.getObject("CHANNEL", Integer.class),
                SampleStatus.valueOf(row.getString("STATUS")),
                row.getLong("TOTAL")));
  }
}
