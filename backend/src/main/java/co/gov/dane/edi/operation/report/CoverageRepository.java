package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Una sola consulta agrupada sobre la muestra con el estado de cada funcionario (criterio único,
 * {@link SampleStatus#STATUS_CASE}), con los mismos joins y filtro que los listados nominales
 * ({@link SampleQueryParts}). Los nombres de tabla llegan ya validados por {@code
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
    StringBuilder sql =
        new StringBuilder(
            "SELECT TO_NUMBER(m.COD_DEPTO) AS DEPARTMENT_CODE, d.NOM_DEPTO AS DEPARTMENT_NAME,"
                + " TO_NUMBER(m.COD_ENT) AS ENTITY_CODE, ent.ENTIDAD AS ENTITY_NAME,"
                + " m.AREA_TRA AS LEVEL_CODE, m.TC1 AS CHANNEL, ");
    sql.append(SampleStatus.STATUS_CASE).append(" AS STATUS, COUNT(*) AS TOTAL FROM ");
    sql.append(survey.sampleTableName()).append(" m");
    Map<Chapter, String> chapterTables = new EnumMap<>(Chapter.class);
    chapterTables.put(Chapter.B, chapterBTable);
    chapterTables.put(Chapter.H, chapterHTable);
    SampleQueryParts.appendChapterJoins(sql, chapterTables);
    // LEFT JOIN: un COD_DEPTO fuera del catálogo debe contar igual que en Novedades y Claves.
    sql.append(" LEFT JOIN GRAL_DEPARTAMENTOS d ON d.COD_DEPTO = TO_NUMBER(m.COD_DEPTO)");
    SampleQueryParts.appendEntityJoin(sql);
    SampleQueryParts.appendSampleFilter(sql, parameters, year, reportSurvey, departments, null);
    sql.append(
        " GROUP BY TO_NUMBER(m.COD_DEPTO), d.NOM_DEPTO, TO_NUMBER(m.COD_ENT), ent.ENTIDAD,"
            + " m.AREA_TRA, m.TC1, ");
    sql.append(SampleStatus.STATUS_CASE);
    return jdbc.query(
        sql.toString(),
        parameters,
        (row, index) -> {
          int departmentCode = row.getInt("DEPARTMENT_CODE");
          String departmentName = row.getString("DEPARTMENT_NAME");
          return new CoverageRow(
              departmentCode,
              departmentName == null || departmentName.isBlank()
                  ? "Departamento " + departmentCode
                  : departmentName,
              row.getLong("ENTITY_CODE"),
              row.getString("ENTITY_NAME"),
              row.getObject("LEVEL_CODE", Integer.class),
              row.getObject("CHANNEL", Integer.class),
              SampleStatus.valueOf(row.getString("STATUS")),
              row.getLong("TOTAL"));
        });
  }
}
