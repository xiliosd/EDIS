package co.gov.dane.edi.repository;

import co.gov.dane.edi.config.SurveyProperties;
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
      "SELECT COLUMN_NAME FROM ALL_TAB_COLUMNS WHERE OWNER = :owner AND TABLE_NAME = :tableName";

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;
  private final Map<String, Set<String>> columnsByTable = new ConcurrentHashMap<>();

  public AnswerColumnRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  public Set<String> findColumns(String tableName) {
    return columnsByTable.computeIfAbsent(tableName, this::loadColumns);
  }

  public void evict(String tableName) {
    columnsByTable.remove(tableName);
  }

  private Set<String> loadColumns(String tableName) {
    return Set.copyOf(
        jdbc.queryForList(
            COLUMNS_OF_TABLE,
            Map.of("owner", survey.dbSchema(), "tableName", tableName),
            String.class));
  }
}
