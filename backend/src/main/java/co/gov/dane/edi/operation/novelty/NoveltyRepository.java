package co.gov.dane.edi.operation.novelty;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.operation.novelty.dto.NoveltyPerson;
import co.gov.dane.edi.operation.report.ReportSurvey;
import co.gov.dane.edi.operation.report.SampleQueryParts;
import co.gov.dane.edi.operation.report.SampleStatus;
import co.gov.dane.edi.util.Latin9Text;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lecturas y escrituras de la gestión de novedades sobre la muestra. Única escritura: IDFNOV y
 * NOV_OTRA_CAUSA de EDIN_MUESTRA, por cédula, año y departamentos del usuario. La tabla sale de
 * {@link SurveyProperties#sampleTableName()}; todo valor va como parámetro.
 */
@Repository
public class NoveltyRepository {

  /** Novedad del catálogo (código y nombre). */
  public record CatalogRow(int code, String name) {}

  /** Entidad con su muestra activa. */
  public record EntityRow(long code, String name, long sampleSize, long withNovelty) {}

  /**
   * Novedad guardada de un funcionario, con la causa cruda (sin decodificar), y su IDMUES
   * (identificador no personal).
   */
  public record CurrentNovelty(BigDecimal noveltyCode, String storedText, String sampleId) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;

  public NoveltyRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  /** Catálogo de la encuesta y el año; un código repetido en GRAL_NOVEDAD aparece una vez. */
  public List<CatalogRow> findCatalog(int year) {
    return jdbc.query(
        "SELECT IDFNOV, MAX(NOVEDAD) AS NOVEDAD FROM GRAL_NOVEDAD"
            + " WHERE PROY LIKE :noveltySurvey AND ANIO = :year GROUP BY IDFNOV ORDER BY IDFNOV",
        Map.of("noveltySurvey", survey.code() + "%", "year", year),
        (row, index) -> new CatalogRow(row.getInt("IDFNOV"), row.getString("NOVEDAD")));
  }

  /**
   * Entidades con funcionarios activos en los departamentos. GRAL_ENTI se agrega por código para
   * que un código repetido en el catálogo no duplique funcionarios.
   */
  public List<EntityRow> findEntities(int year, List<Integer> departments) {
    Map<String, Object> parameters = new HashMap<>();
    StringBuilder sql =
        new StringBuilder(
            "SELECT TO_NUMBER(m.COD_ENT) AS ENTITY_CODE, MAX(ent.ENTIDAD) AS ENTITY_NAME,"
                + " COUNT(*) AS SAMPLE_SIZE, COUNT(m.IDFNOV) AS WITH_NOVELTY FROM ");
    sql.append(survey.sampleTableName()).append(" m");
    sql.append(
        " LEFT JOIN (SELECT COD_ENT, MAX(ENTIDAD) AS ENTIDAD FROM GRAL_ENTI GROUP BY COD_ENT) ent"
            + " ON ent.COD_ENT = TO_NUMBER(m.COD_ENT)");
    SampleQueryParts.appendSampleFilter(
        sql, parameters, year, ReportSurvey.GENERAL, departments, null);
    sql.append(
        " GROUP BY TO_NUMBER(m.COD_ENT) ORDER BY MAX(ent.ENTIDAD) NULLS LAST,"
            + " TO_NUMBER(m.COD_ENT)");
    return jdbc.query(
        sql.toString(),
        parameters,
        (row, index) -> {
          long code = row.getLong("ENTITY_CODE");
          String name = row.getString("ENTITY_NAME");
          return new EntityRow(
              code,
              name == null || name.isBlank() ? "Entidad " + code : name,
              row.getLong("SAMPLE_SIZE"),
              row.getLong("WITH_NOVELTY"));
        });
  }

  /**
   * Funcionarios activos de una entidad con su estado ({@link SampleStatus#STATUS_CASE}) y novedad.
   * La causa se devuelve decodificada, tal como la escribió quien la registró.
   *
   * @param chapterTables tablas de los capítulos B y H del año
   */
  public List<NoveltyPerson> findPeople(
      Map<Chapter, String> chapterTables, int year, List<Integer> departments, long entityCode) {
    Map<String, Object> parameters = new HashMap<>();
    StringBuilder sql =
        new StringBuilder(
            "SELECT m.C_C AS RESPONDENT_CODE, m.NOM_APE AS PERSON_NAME, m.AREA_TRA AS LEVEL_CODE,"
                + " m.IDFNOV AS NOVELTY_CODE, m.NOV_OTRA_CAUSA AS OTHER_CAUSE, ");
    sql.append(SampleStatus.STATUS_CASE).append(" AS STATUS");
    sql.append(" FROM ").append(survey.sampleTableName()).append(" m");
    SampleQueryParts.appendChapterJoins(sql, chapterTables);
    SampleQueryParts.appendSampleFilter(
        sql, parameters, year, ReportSurvey.GENERAL, departments, entityCode);
    sql.append(" ORDER BY m.NOM_APE, m.C_C");
    return jdbc.query(
        sql.toString(),
        parameters,
        (row, index) -> {
          BigDecimal novelty = row.getBigDecimal("NOVELTY_CODE");
          return new NoveltyPerson(
              row.getString("RESPONDENT_CODE"),
              row.getString("PERSON_NAME"),
              row.getObject("LEVEL_CODE", Integer.class),
              SampleStatus.valueOf(row.getString("STATUS")),
              novelty == null ? null : novelty.intValue(),
              Latin9Text.decodeNumericReferences(row.getString("OTHER_CAUSE")));
        });
  }

  /**
   * Cambia la novedad solo si la fila sigue con los valores crudos leídos con {@link
   * #findCurrentForUpdate}. {@code DECODE} compara NULL con NULL como iguales.
   *
   * @param departments departamentos propios del usuario (nunca nulo: la escritura no aplica el
   *     Ruling 3)
   * @param noveltyCode nueva novedad; nula para quitarla
   * @param storedText causa ya convertida con {@link Latin9Text#encode}; nula si no aplica
   * @param readCode IDFNOV tal como se leyó
   * @param readText NOV_OTRA_CAUSA tal como se leyó (sin decodificar)
   * @return filas actualizadas
   */
  public int updateNovelty(
      int year,
      String respondentCode,
      List<Integer> departments,
      Integer noveltyCode,
      String storedText,
      BigDecimal readCode,
      String readText) {
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("noveltyCode", noveltyCode, Types.NUMERIC)
            .addValue("storedText", storedText, Types.VARCHAR)
            .addValue("respondentCode", respondentCode, Types.VARCHAR)
            .addValue("year", String.valueOf(year), Types.VARCHAR)
            .addValue("readCode", readCode, Types.NUMERIC)
            .addValue("readText", readText, Types.VARCHAR);
    StringBuilder sql = new StringBuilder("UPDATE ");
    sql.append(survey.sampleTableName())
        .append(
            " SET IDFNOV = :noveltyCode, NOV_OTRA_CAUSA = :storedText"
                + " WHERE C_C = :respondentCode AND ANIO = :year");
    appendDepartmentFilter(sql, parameters, departments);
    sql.append(
        " AND DECODE(IDFNOV, :readCode, 1, 0) = 1"
            + " AND DECODE(NOV_OTRA_CAUSA, :readText, 1, 0) = 1");
    return jdbc.update(sql.toString(), parameters);
  }

  /**
   * La novedad guardada (cruda) de un funcionario de los departamentos indicados, bloqueando la
   * fila hasta el fin de la transacción; vacío si no existe en el año o es de otro departamento
   * (quien llama no debe distinguir los dos casos).
   */
  public Optional<CurrentNovelty> findCurrentForUpdate(
      int year, String respondentCode, List<Integer> departments) {
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("respondentCode", respondentCode, Types.VARCHAR)
            .addValue("year", String.valueOf(year), Types.VARCHAR);
    StringBuilder sql = new StringBuilder("SELECT IDFNOV, NOV_OTRA_CAUSA, IDMUES FROM ");
    sql.append(survey.sampleTableName()).append(" WHERE C_C = :respondentCode AND ANIO = :year");
    appendDepartmentFilter(sql, parameters, departments);
    sql.append(" FOR UPDATE");
    return jdbc
        .query(
            sql.toString(),
            parameters,
            (row, index) ->
                new CurrentNovelty(
                    row.getBigDecimal("IDFNOV"),
                    row.getString("NOV_OTRA_CAUSA"),
                    row.getString("IDMUES")))
        .stream()
        .findFirst();
  }

  private static void appendDepartmentFilter(
      StringBuilder sql, MapSqlParameterSource parameters, List<Integer> departments) {
    if (departments != null) {
      sql.append(" AND TO_NUMBER(COD_DEPTO) IN (:departments)");
      parameters.addValue("departments", departments);
    }
  }
}
