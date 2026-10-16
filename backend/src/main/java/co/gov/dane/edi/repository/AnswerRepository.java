package co.gov.dane.edi.repository;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lectura y escritura de respuestas en las tablas heredadas (una fila por encuestado y capítulo).
 * Solo los nombres de tabla (Chapter) y de columna (lista blanca) se concatenan; todo valor va
 * enlazado.
 */
@Repository
public class AnswerRepository {

  private static final Pattern COLUMN = Pattern.compile("[A-Z][0-9A-Z_]{1,29}");
  private static final String YEAR_COLUMN = "ANIO";
  private static final String OBSERVATIONS_COLUMN = "OBS";

  private final NamedParameterJdbcTemplate jdbc;
  private final AnswerColumnRepository answerColumns;
  private final SurveyProperties survey;

  public AnswerRepository(
      NamedParameterJdbcTemplate jdbc,
      AnswerColumnRepository answerColumns,
      SurveyProperties survey) {
    this.jdbc = jdbc;
    this.answerColumns = answerColumns;
    this.survey = survey;
  }

  public Map<String, String> findAnswers(
      Chapter chapter, String respondentCode, Collection<String> columns) {
    List<String> safe = requireColumns(chapter, columns);
    if (safe.isEmpty()) {
      return Map.of();
    }
    String sql =
        "SELECT " + String.join(", ", safe) + " FROM " + chapter.tableName() + " WHERE C_C = :cc";
    List<Map<String, String>> rows =
        jdbc.query(
            sql,
            Map.of("cc", respondentCode),
            (row, i) -> {
              Map<String, String> values = new HashMap<>();
              for (String column : safe) {
                String value = row.getString(column);
                if (value != null) {
                  values.put(column, value);
                }
              }
              return values;
            });
    return rows.isEmpty() ? Map.of() : Map.copyOf(rows.get(0));
  }

  public void insertPage(Chapter chapter, String respondentCode, Map<String, String> values) {
    List<String> safe = requireColumns(chapter, values.keySet());
    List<String> names = new ArrayList<>(List.of("C_C", "ESTADO", "TC"));
    List<String> params = new ArrayList<>(List.of(":cc", "1", "1"));
    MapSqlParameterSource parameters = new MapSqlParameterSource("cc", respondentCode);
    if (answerColumns.findColumns(chapter.tableName()).contains(YEAR_COLUMN)) {
      names.add(YEAR_COLUMN);
      params.add(":year");
      parameters.addValue("year", survey.yearText());
    }
    for (int i = 0; i < safe.size(); i++) {
      names.add(safe.get(i));
      params.add(":v" + i);
      parameters.addValue("v" + i, values.get(safe.get(i)));
    }
    jdbc.update(
        "INSERT INTO "
            + chapter.tableName()
            + " ("
            + String.join(", ", names)
            + ") VALUES ("
            + String.join(", ", params)
            + ")",
        parameters);
  }

  /** Escritura única (D16): solo actualiza si la columna guarda de la página sigue en NULL. */
  public int updatePage(
      Chapter chapter, String respondentCode, Map<String, String> values, String guardColumn) {
    List<String> safe = requireColumns(chapter, values.keySet());
    String guard = requireColumns(chapter, List.of(guardColumn)).get(0);
    MapSqlParameterSource parameters = new MapSqlParameterSource("cc", respondentCode);
    List<String> assignments = new ArrayList<>();
    for (int i = 0; i < safe.size(); i++) {
      assignments.add(safe.get(i) + " = :v" + i);
      parameters.addValue("v" + i, values.get(safe.get(i)));
    }
    return jdbc.update(
        "UPDATE "
            + chapter.tableName()
            + " SET "
            + String.join(", ", assignments)
            + " WHERE C_C = :cc AND "
            + guard
            + " IS NULL",
        parameters);
  }

  public String findObservations(String respondentCode) {
    return findAnswers(Chapter.H, respondentCode, List.of(OBSERVATIONS_COLUMN))
        .get(OBSERVATIONS_COLUMN);
  }

  public int saveObservations(String respondentCode, String text) {
    return jdbc.update(
        "UPDATE "
            + Chapter.H.tableName()
            + " SET "
            + OBSERVATIONS_COLUMN
            + " = :obs WHERE C_C = :cc AND "
            + OBSERVATIONS_COLUMN
            + " IS NULL",
        Map.of("obs", text, "cc", respondentCode));
  }

  private List<String> requireColumns(Chapter chapter, Collection<String> columns) {
    Set<String> known = answerColumns.findColumns(chapter.tableName());
    List<String> safe = new ArrayList<>();
    for (String column : columns) {
      if (column == null || !COLUMN.matcher(column).matches() || !known.contains(column)) {
        throw new IllegalArgumentException("Columna no permitida en " + chapter.tableName());
      }
      safe.add(column);
    }
    return safe;
  }
}
