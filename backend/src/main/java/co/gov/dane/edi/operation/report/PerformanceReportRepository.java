package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.operation.report.dto.ChapterTimestamp;
import co.gov.dane.edi.operation.report.dto.PerformanceRow;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Fecha y hora de guardado de cada capítulo de los funcionarios que iniciaron (B07 no nulo). Una
 * sola consulta para todos los departamentos pedidos.
 */
@Repository
public class PerformanceReportRepository {

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;

  public PerformanceReportRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  /**
   * @param chapterTables los siete capítulos B a H
   * @param departments departamentos a incluir; nulo para no filtrar por departamento
   */
  public List<PerformanceRow> findRows(
      Map<Chapter, String> chapterTables,
      int year,
      ReportSurvey reportSurvey,
      List<Integer> departments) {
    Map<String, Object> parameters = new HashMap<>();
    StringBuilder sql =
        new StringBuilder(
            "SELECT TO_NUMBER(m.COD_ENT) AS ENTITY_CODE, ent.ENTIDAD AS ENTITY_NAME,"
                + " m.C_C AS RESPONDENT_CODE, m.NOM_APE AS PERSON_NAME,"
                + " m.AREA_TRA AS LEVEL_CODE");
    for (Chapter chapter : Chapter.values()) {
      sql.append(", ")
          .append(SampleQueryParts.alias(chapter))
          .append(".SAVED_AT AS SAVED_")
          .append(chapter.code());
    }
    sql.append(" FROM ").append(survey.sampleTableName()).append(" m");
    SampleQueryParts.appendChapterJoins(sql, chapterTables);
    SampleQueryParts.appendEntityJoin(sql);
    SampleQueryParts.appendSampleFilter(sql, parameters, year, reportSurvey, departments, null);
    sql.append(" AND b.B07 IS NOT NULL");
    sql.append(" ORDER BY ent.ENTIDAD NULLS LAST, TO_NUMBER(m.COD_ENT), m.NOM_APE, m.C_C");
    return jdbc.query(
        sql.toString(),
        parameters,
        (row, index) -> {
          List<ChapterTimestamp> chapters = new ArrayList<>();
          for (Chapter chapter : Chapter.values()) {
            Timestamp saved = row.getTimestamp("SAVED_" + chapter.code());
            LocalDateTime savedAt = saved == null ? null : saved.toLocalDateTime();
            chapters.add(
                new ChapterTimestamp(
                    chapter.code(),
                    savedAt == null ? null : savedAt.toLocalDate(),
                    savedAt == null ? null : savedAt.toLocalTime()));
          }
          long entityCode = row.getLong("ENTITY_CODE");
          String entityName = row.getString("ENTITY_NAME");
          return new PerformanceRow(
              entityCode,
              entityName == null || entityName.isBlank() ? "Entidad " + entityCode : entityName,
              row.getString("RESPONDENT_CODE"),
              row.getString("PERSON_NAME"),
              row.getObject("LEVEL_CODE", Integer.class),
              List.copyOf(chapters));
        });
  }
}
