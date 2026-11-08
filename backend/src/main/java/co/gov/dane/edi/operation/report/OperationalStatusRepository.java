package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.operation.report.dto.PersonStatusRow;
import co.gov.dane.edi.operation.report.dto.StatusFilter;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Listado nominal de la muestra de un departamento con el estado de cada funcionario (criterio
 * único, {@link SampleStatus#STATUS_CASE}). Los nombres de tabla llegan validados por {@code
 * AnswerTableResolver} y {@code SurveyProperties}; todo valor va como parámetro.
 */
@Repository
public class OperationalStatusRepository {

  private static final int WEB_CHANNEL = 1;
  private static final int DMC_CHANNEL = 2;

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;

  public OperationalStatusRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  /**
   * @param chapterTables tabla de cada capítulo
   * @param limit filas por página; nulo para traer todas
   */
  public List<PersonStatusRow> findRows(
      Map<Chapter, String> chapterTables,
      int year,
      ReportSurvey reportSurvey,
      int departmentCode,
      Long entityCode,
      StatusFilter statusFilter,
      long offset,
      Integer limit) {
    Map<String, Object> parameters = new HashMap<>();
    StringBuilder sql =
        new StringBuilder(
            "SELECT TO_NUMBER(m.COD_ENT) AS ENTITY_CODE, e.ENTIDAD AS ENTITY_NAME,"
                + " m.C_C AS RESPONDENT_CODE, m.NOM_APE AS PERSON_NAME,"
                + " m.AREA_TRA AS LEVEL_CODE, m.TC1 AS CHANNEL, ");
    sql.append(SampleStatus.STATUS_CASE).append(" AS STATUS, h.COMPLETED_AT AS COMPLETED_AT,");
    sql.append(" m.IDFNOV AS NOVELTY_CODE, n.NOVEDAD AS NOVELTY_NAME,");
    sql.append(" m.NOV_OTRA_CAUSA AS OTHER_CAUSE");
    for (Chapter chapter : Chapter.values()) {
      sql.append(", CASE WHEN ")
          .append(alias(chapter))
          .append(".ROWS_FOUND IS NULL THEN 1 ELSE 0 END AS MISSING_")
          .append(chapter.code());
    }
    appendFrom(sql, parameters, chapterTables, year, reportSurvey, departmentCode, entityCode);
    appendStatus(sql, parameters, statusFilter);
    sql.append(" ORDER BY e.ENTIDAD NULLS LAST, TO_NUMBER(m.COD_ENT), m.TC1, m.NOM_APE, m.C_C");
    if (limit != null) {
      sql.append(" OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY");
      parameters.put("offset", offset);
      parameters.put("limit", limit);
    }
    return jdbc.query(sql.toString(), parameters, (row, index) -> mapRow(row));
  }

  public long count(
      Map<Chapter, String> chapterTables,
      int year,
      ReportSurvey reportSurvey,
      int departmentCode,
      Long entityCode,
      StatusFilter statusFilter) {
    Map<String, Object> parameters = new HashMap<>();
    StringBuilder sql = new StringBuilder("SELECT COUNT(*)");
    appendFrom(sql, parameters, chapterTables, year, reportSurvey, departmentCode, entityCode);
    appendStatus(sql, parameters, statusFilter);
    Long total = jdbc.queryForObject(sql.toString(), parameters, Long.class);
    return total == null ? 0 : total;
  }

  private void appendFrom(
      StringBuilder sql,
      Map<String, Object> parameters,
      Map<Chapter, String> chapterTables,
      int year,
      ReportSurvey reportSurvey,
      int departmentCode,
      Long entityCode) {
    sql.append(" FROM ").append(survey.sampleTableName()).append(" m");
    // Los capítulos no tienen clave única en C_C: se une una subconsulta agregada por C_C (una
    // fila por funcionario) para que una fila repetida no duplique personas.
    for (Chapter chapter : Chapter.values()) {
      sql.append(" LEFT JOIN (SELECT C_C, COUNT(*) AS ROWS_FOUND");
      if (chapter == Chapter.B) {
        sql.append(", MAX(B07) AS B07");
      } else if (chapter == Chapter.H) {
        sql.append(", MAX(H06) AS H06, MAX(FECHA) AS COMPLETED_AT");
      }
      sql.append(" FROM ")
          .append(chapterTables.get(chapter))
          .append(" GROUP BY C_C) ")
          .append(alias(chapter))
          .append(" ON ")
          .append(alias(chapter))
          .append(".C_C = m.C_C");
    }
    sql.append(" LEFT JOIN GRAL_ENTI e ON e.COD_ENT = TO_NUMBER(m.COD_ENT)");
    sql.append(
        " LEFT JOIN (SELECT IDFNOV, MAX(NOVEDAD) AS NOVEDAD FROM GRAL_NOVEDAD"
            + " WHERE PROY LIKE :noveltySurvey AND ANIO = :noveltyYear GROUP BY IDFNOV) n"
            + " ON n.IDFNOV = m.IDFNOV");
    parameters.put("noveltySurvey", survey.code() + "%");
    parameters.put("noveltyYear", year);
    sql.append(" WHERE m.ANIO = :year AND m.ATV = 1 AND TO_NUMBER(m.COD_DEPTO) = :department");
    parameters.put("year", String.valueOf(year));
    parameters.put("department", departmentCode);
    if (reportSurvey.encCode() != null) {
      sql.append(" AND m.ENC = :enc");
      parameters.put("enc", reportSurvey.encCode());
    }
    if (entityCode != null) {
      sql.append(" AND TO_NUMBER(m.COD_ENT) = :entity");
      parameters.put("entity", entityCode);
    }
  }

  private static void appendStatus(
      StringBuilder sql, Map<String, Object> parameters, StatusFilter statusFilter) {
    if (statusFilter.status() != null) {
      sql.append(" AND (").append(SampleStatus.STATUS_CASE).append(") = :status");
      parameters.put("status", statusFilter.status().name());
    }
  }

  /** Alias SQL del capítulo: {@code b} y {@code h} los exige {@link SampleStatus#STATUS_CASE}. */
  private static String alias(Chapter chapter) {
    return chapter.name().toLowerCase(Locale.ROOT);
  }

  private static PersonStatusRow mapRow(ResultSet row) throws SQLException {
    SampleStatus status = SampleStatus.valueOf(row.getString("STATUS"));
    Timestamp completedAt = row.getTimestamp("COMPLETED_AT");
    BigDecimal noveltyCode = row.getBigDecimal("NOVELTY_CODE");
    List<String> missing = new ArrayList<>();
    for (Chapter chapter : Chapter.values()) {
      if (row.getInt("MISSING_" + chapter.code()) == 1) {
        missing.add(chapter.code());
      }
    }
    String entityName = row.getString("ENTITY_NAME");
    long entityCode = row.getLong("ENTITY_CODE");
    return new PersonStatusRow(
        entityCode,
        entityName == null || entityName.isBlank() ? "Entidad " + entityCode : entityName,
        row.getString("RESPONDENT_CODE"),
        row.getString("PERSON_NAME"),
        row.getObject("LEVEL_CODE", Integer.class),
        loadType(row.getObject("CHANNEL", Integer.class)),
        status,
        status == SampleStatus.COMPLETE && completedAt != null
            ? completedAt.toLocalDateTime()
            : null,
        noveltyCode == null ? null : noveltyCode.intValue(),
        row.getString("NOVELTY_NAME"),
        row.getString("OTHER_CAUSE"),
        List.copyOf(missing));
  }

  private static String loadType(Integer channel) {
    if (channel == null) {
      return null;
    }
    return switch (channel) {
      case WEB_CHANNEL -> "WEB";
      case DMC_CHANNEL -> "DMC";
      default -> null;
    };
  }
}
