package co.gov.dane.edi.repository;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.AnswerColumn;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lista blanca de columnas de respuesta. Las preguntas se agregan desde el administrador sin tocar
 * el código, así que el diccionario de Oracle es la única fuente confiable de qué columnas existen.
 */
@Repository
public class AnswerColumnRepository {

  private static final String COLUMNS_OF_TABLE =
      "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH FROM ALL_TAB_COLUMNS"
          + " WHERE OWNER = :owner AND TABLE_NAME = :tableName";

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;
  private final Map<String, Map<String, AnswerColumn>> columnsByTable = new ConcurrentHashMap<>();

  public AnswerColumnRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  public Set<String> findColumns(String tableName) {
    return findColumnDetails(tableName).keySet();
  }

  public Map<String, AnswerColumn> findColumnDetails(String tableName) {
    return columnsByTable.computeIfAbsent(tableName, this::loadColumns);
  }

  public void evict(String tableName) {
    columnsByTable.remove(tableName);
  }

  private Map<String, AnswerColumn> loadColumns(String tableName) {
    Map<String, AnswerColumn> columns = new LinkedHashMap<>();
    jdbc.query(
        COLUMNS_OF_TABLE,
        Map.of("owner", survey.dbSchema(), "tableName", tableName),
        row -> {
          AnswerColumn column =
              new AnswerColumn(
                  row.getString("COLUMN_NAME"),
                  row.getString("DATA_TYPE"),
                  row.getInt("DATA_LENGTH"));
          columns.put(column.name(), column);
        });
    return Map.copyOf(columns);
  }
}
