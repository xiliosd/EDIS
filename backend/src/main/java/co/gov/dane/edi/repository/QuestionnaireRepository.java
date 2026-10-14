package co.gov.dane.edi.repository;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.AnswerOption;
import co.gov.dane.edi.model.HeaderKind;
import co.gov.dane.edi.model.SectionHeader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionnaireRepository {

  // Igual que Capitulos_Body.php; REGEXP_REPLACE cambia el comodín "200X" por el año.
  private static final String ROWS_BY_PAGE =
      """
      SELECT P.COD_PRE, PP.COD_CAP_PROY, REGEXP_REPLACE(P.PREGUNTA, '200.', :yearText) AS PREGUNTA,
             P.TIPO, PER.NUMERAL, O.GRUPO AS GRUPO_RESPUESTA, S.SALTO, S.VALOR_SALTO, O.COD_OBJ
        FROM FRM_CAP_PROY PP
        JOIN FRM_PRE_GRUPO G ON G.COD_PRE_GRUPO = PP.COD_PRE_GRUPO
        JOIN FRM_PREGUNTA P ON P.COD_PRE = G.COD_PRE
        JOIN FRM_CAP_PERIODO PER ON PER.COD_CAP_PROY = PP.COD_CAP_PROY
                                AND PER.PROY LIKE :projectPattern AND PER.ANIO = :yearText
        LEFT JOIN FRM_SALTO S ON S.COD_CAP_PROY = PP.COD_CAP_PROY
        LEFT JOIN FRM_OBJ_PRE O ON O.COD_CAP_PROY = PP.COD_CAP_PROY
       WHERE PP.CAPITULO = :pageCode
       ORDER BY G.GRUPO, G.ORDEN
      """;

  private static final String HEADERS =
      """
      SELECT COD_CAP_PROY, TIPO, ENCABEZADO
        FROM FRM_ENCABEZADO
       WHERE COD_CAP_PROY IN (:entryIds) AND ANIO = :yearText
       ORDER BY COD_CAP_PROY, TIPO DESC, COD_ENC
      """;

  private static final String OPTIONS =
      """
      SELECT G.GRUPO, R.VALOR, R.RESPUESTA
        FROM FRM_RTA_GRUPO G
        JOIN FRM_RESPUESTA R ON R.COD_RTA = G.COD_RTA
       WHERE G.GRUPO IN (:groups)
       ORDER BY G.GRUPO, TO_NUMBER(G.ORDEN)
      """;

  private static final String HEADING_TYPE = "E";

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;

  public QuestionnaireRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  public List<QuestionnaireRow> findRowsByPage(String pageCode) {
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("yearText", survey.yearText())
            .addValue("projectPattern", "%" + survey.code() + "%")
            .addValue("pageCode", pageCode);
    return jdbc.query(
        ROWS_BY_PAGE,
        parameters,
        (row, index) ->
            new QuestionnaireRow(
                row.getLong("COD_PRE"),
                row.getLong("COD_CAP_PROY"),
                row.getString("PREGUNTA"),
                row.getString("TIPO"),
                row.getString("NUMERAL"),
                row.getObject("GRUPO_RESPUESTA", Integer.class),
                row.getObject("SALTO", Integer.class),
                row.getString("VALOR_SALTO"),
                row.getObject("COD_OBJ", Integer.class)));
  }

  /** Oracle admite hasta 1000 valores en IN; una página del cuestionario tiene muchos menos. */
  public Map<Long, List<SectionHeader>> findHeaders(Collection<Long> entryIds) {
    Map<Long, List<SectionHeader>> headers = new LinkedHashMap<>();
    if (entryIds.isEmpty()) {
      return headers;
    }
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("entryIds", entryIds)
            .addValue("yearText", survey.yearText());
    jdbc.query(
        HEADERS,
        parameters,
        row -> {
          HeaderKind kind =
              HEADING_TYPE.equals(row.getString("TIPO")) ? HeaderKind.HEADING : HeaderKind.SUBTITLE;
          headers
              .computeIfAbsent(row.getLong("COD_CAP_PROY"), ignored -> new ArrayList<>())
              .add(new SectionHeader(kind, row.getString("ENCABEZADO")));
        });
    return headers;
  }

  public Map<Integer, List<AnswerOption>> findOptions(Collection<Integer> optionGroups) {
    Map<Integer, List<AnswerOption>> options = new LinkedHashMap<>();
    if (optionGroups.isEmpty()) {
      return options;
    }
    jdbc.query(
        OPTIONS,
        new MapSqlParameterSource("groups", optionGroups),
        row -> {
          options
              .computeIfAbsent(row.getInt("GRUPO"), ignored -> new ArrayList<>())
              .add(new AnswerOption(row.getString("VALOR"), row.getString("RESPUESTA")));
        });
    return options;
  }
}
