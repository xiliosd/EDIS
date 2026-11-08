package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Piezas SQL que comparten los reportes nominales sobre la muestra (estado, novedades y
 * rendimientos): joins de capítulos, catálogo de novedades, entidad y filtros de la muestra. Los
 * nombres de tabla llegan validados por {@code AnswerTableResolver} y {@code SurveyProperties};
 * todo valor va como parámetro. Alias fijos: {@code m} muestra, {@code ent} entidad, {@code n}
 * novedad y uno por capítulo ({@link #alias(Chapter)}). Público para la gestión de novedades\n *
 * ({@code operation.novelty}), que lista la misma muestra con el mismo criterio de estado.
 */
public final class SampleQueryParts {

  private SampleQueryParts() {}

  /** Alias SQL del capítulo: {@code b} y {@code h} los exige {@link SampleStatus#STATUS_CASE}. */
  public static String alias(Chapter chapter) {
    return chapter.name().toLowerCase(Locale.ROOT);
  }

  /**
   * Une cada capítulo de {@code chapterTables} como subconsulta agregada por C_C (una fila por
   * funcionario, Ruling 4: las tablas de capítulo no tienen clave única en C_C). Columnas de cada
   * alias: {@code ROWS_FOUND}, {@code SAVED_AT} (último {@code FECHA}); en B además {@code B07} y
   * en H {@code H06}.
   */
  public static void appendChapterJoins(StringBuilder sql, Map<Chapter, String> chapterTables) {
    for (Map.Entry<Chapter, String> entry : chapterTables.entrySet()) {
      Chapter chapter = entry.getKey();
      sql.append(" LEFT JOIN (SELECT C_C, COUNT(*) AS ROWS_FOUND, MAX(FECHA) AS SAVED_AT");
      if (chapter == Chapter.B) {
        sql.append(", MAX(B07) AS B07");
      } else if (chapter == Chapter.H) {
        sql.append(", MAX(H06) AS H06");
      }
      sql.append(" FROM ")
          .append(entry.getValue())
          .append(" GROUP BY C_C) ")
          .append(alias(chapter))
          .append(" ON ")
          .append(alias(chapter))
          .append(".C_C = m.C_C");
    }
  }

  /** Une la entidad ({@code ent}) con la muestra. */
  public static void appendEntityJoin(StringBuilder sql) {
    sql.append(" LEFT JOIN GRAL_ENTI ent ON ent.COD_ENT = TO_NUMBER(m.COD_ENT)");
  }

  /** Une el catálogo de novedades de la encuesta y el año ({@code n.NOVEDAD}), uno por IDFNOV. */
  public static void appendNoveltyCatalogJoin(
      StringBuilder sql, Map<String, Object> parameters, SurveyProperties survey, int year) {
    sql.append(
        " LEFT JOIN (SELECT IDFNOV, MAX(NOVEDAD) AS NOVEDAD FROM GRAL_NOVEDAD"
            + " WHERE PROY LIKE :noveltySurvey AND ANIO = :noveltyYear GROUP BY IDFNOV) n"
            + " ON n.IDFNOV = m.IDFNOV");
    parameters.put("noveltySurvey", survey.code() + "%");
    parameters.put("noveltyYear", year);
  }

  /**
   * Filtro de la muestra: año, activos, encuesta, departamentos y entidad.
   *
   * @param departments departamentos a incluir; nulo para no filtrar por departamento (Ruling 3)
   * @param entityCode entidad; nulo para todas
   */
  public static void appendSampleFilter(
      StringBuilder sql,
      Map<String, Object> parameters,
      int year,
      ReportSurvey reportSurvey,
      List<Integer> departments,
      Long entityCode) {
    sql.append(" WHERE m.ANIO = :year AND m.ATV = 1");
    parameters.put("year", String.valueOf(year));
    if (departments != null) {
      sql.append(" AND TO_NUMBER(m.COD_DEPTO) IN (:departments)");
      parameters.put("departments", departments);
    }
    if (reportSurvey.encCode() != null) {
      sql.append(" AND m.ENC = :enc");
      parameters.put("enc", reportSurvey.encCode());
    }
    if (entityCode != null) {
      sql.append(" AND TO_NUMBER(m.COD_ENT) = :entity");
      parameters.put("entity", entityCode);
    }
  }
}
