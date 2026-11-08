package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.config.SurveyProperties;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Años que tienen filas en la tabla de muestra de la encuesta configurada. */
@Repository
public class ReportYearsRepository {

  private final JdbcTemplate jdbc;
  private final SurveyProperties survey;

  public ReportYearsRepository(JdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  /** Años de la muestra, del más reciente al más antiguo. */
  public List<Integer> findYears() {
    // ANIO es texto en algunos ambientes: solo se consideran valores numéricos.
    String sql =
        "SELECT DISTINCT TO_NUMBER(ANIO) AS YEAR_VALUE FROM "
            + survey.sampleTableName()
            + " WHERE ANIO IS NOT NULL AND REGEXP_LIKE(TRIM(ANIO), '^[0-9]{4}$')"
            + " ORDER BY YEAR_VALUE DESC";
    return jdbc.queryForList(sql, Integer.class);
  }
}
