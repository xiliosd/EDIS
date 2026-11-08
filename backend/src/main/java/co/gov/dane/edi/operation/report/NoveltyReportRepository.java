package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.operation.report.dto.NoveltyReportRow;
import co.gov.dane.edi.util.Latin9Text;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Funcionarios con novedad ({@code IDFNOV} no nulo). Una sola consulta para todos los departamentos
 * pedidos; el estado completo usa {@link SampleStatus#STATUS_CASE}.
 */
@Repository
public class NoveltyReportRepository {

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;

  public NoveltyReportRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  /**
   * @param chapterTables tablas de los capítulos B y H
   * @param departments departamentos a incluir; nulo para no filtrar por departamento
   */
  public List<NoveltyReportRow> findRows(
      Map<Chapter, String> chapterTables,
      int year,
      ReportSurvey reportSurvey,
      List<Integer> departments) {
    Map<String, Object> parameters = new HashMap<>();
    StringBuilder sql =
        new StringBuilder(
            "SELECT TO_NUMBER(m.COD_DEPTO) AS DEPARTMENT_CODE, d.NOM_DEPTO AS DEPARTMENT_NAME,"
                + " TO_NUMBER(m.COD_ENT) AS ENTITY_CODE, ent.ENTIDAD AS ENTITY_NAME,"
                + " m.C_C AS RESPONDENT_CODE, m.NOM_APE AS PERSON_NAME,"
                + " m.AREA_TRA AS LEVEL_CODE, m.IDFNOV AS NOVELTY_CODE,"
                + " n.NOVEDAD AS NOVELTY_NAME, m.NOV_OTRA_CAUSA AS OTHER_CAUSE, ");
    sql.append(SampleStatus.STATUS_CASE).append(" AS STATUS");
    sql.append(" FROM ").append(survey.sampleTableName()).append(" m");
    SampleQueryParts.appendChapterJoins(sql, chapterTables);
    SampleQueryParts.appendEntityJoin(sql);
    sql.append(" LEFT JOIN GRAL_DEPARTAMENTOS d ON d.COD_DEPTO = TO_NUMBER(m.COD_DEPTO)");
    SampleQueryParts.appendNoveltyCatalogJoin(sql, parameters, survey, year);
    SampleQueryParts.appendSampleFilter(sql, parameters, year, reportSurvey, departments, null);
    sql.append(" AND m.IDFNOV IS NOT NULL");
    sql.append(
        " ORDER BY d.NOM_DEPTO NULLS LAST, TO_NUMBER(m.COD_DEPTO), ent.ENTIDAD NULLS LAST,"
            + " TO_NUMBER(m.COD_ENT), m.NOM_APE, m.C_C");
    return jdbc.query(
        sql.toString(),
        parameters,
        (row, index) -> {
          long entityCode = row.getLong("ENTITY_CODE");
          int departmentCode = row.getInt("DEPARTMENT_CODE");
          String entityName = row.getString("ENTITY_NAME");
          String departmentName = row.getString("DEPARTMENT_NAME");
          return new NoveltyReportRow(
              departmentCode,
              departmentName == null || departmentName.isBlank()
                  ? "Departamento " + departmentCode
                  : departmentName,
              entityCode,
              entityName == null || entityName.isBlank() ? "Entidad " + entityCode : entityName,
              row.getString("RESPONDENT_CODE"),
              row.getString("PERSON_NAME"),
              row.getObject("LEVEL_CODE", Integer.class),
              row.getBigDecimal("NOVELTY_CODE").intValue(),
              row.getString("NOVELTY_NAME"),
              Latin9Text.decodeNumericReferences(row.getString("OTHER_CAUSE")),
              SampleStatus.COMPLETE.name().equals(row.getString("STATUS")));
        });
  }
}
