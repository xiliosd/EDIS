package co.gov.dane.edi.admin;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.service.error.AnswerValidationException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tabla de respuestas de cada capítulo por encuesta y año. El nombre se concatena en el SQL, así
 * que solo se entrega si cumple el patrón, existe en el esquema configurado y trae las columnas
 * mínimas C_C, ESTADO y TC (D3: la aplicación nunca crea tablas).
 */
@Component
public class AnswerTableResolver {

  private static final Pattern TABLE = Pattern.compile("[A-Z][A-Z0-9_]{1,29}");
  private static final int REQUIRED_COLUMNS = 3;
  private static final String HAS_REQUIRED_COLUMNS =
      "SELECT COUNT(*) FROM ALL_TAB_COLUMNS c JOIN ALL_TABLES t"
          + " ON t.OWNER = c.OWNER AND t.TABLE_NAME = c.TABLE_NAME"
          + " WHERE c.OWNER = :owner AND c.TABLE_NAME = :tableName"
          + " AND c.COLUMN_NAME IN ('C_C', 'ESTADO', 'TC')";

  private static final String EXISTS =
      "SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = :owner AND TABLE_NAME = :tableName";

  /** Resultado de revisar un nombre de tabla propuesto para un capítulo. */
  public enum TableCheck {
    /** Existe en el esquema y tiene C_C, ESTADO y TC. */
    VALID,
    /** El nombre es seguro pero la tabla aún no existe (se crea con el script). */
    MISSING,
    /** Existe pero le falta alguna de las columnas mínimas. */
    INCOMPLETE,
    /** El nombre no cumple el patrón: nunca llega al SQL. */
    INVALID_NAME
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;
  private final RulesFileRepository rulesFiles;
  private final Map<String, String> validated = new ConcurrentHashMap<>();

  public AnswerTableResolver(
      NamedParameterJdbcTemplate jdbc, SurveyProperties survey, RulesFileRepository rulesFiles) {
    this.jdbc = jdbc;
    this.survey = survey;
    this.rulesFiles = rulesFiles;
  }

  /** Tabla mapeada en el año o, sin mapeo (o sin archivo), la fija del capítulo. */
  public String tableFor(Chapter chapter, String surveyCode, int year) {
    String key = surveyCode + "-" + year + "-" + chapter.code();
    String cached = validated.get(key);
    if (cached != null) {
      return cached;
    }
    String table =
        rulesFiles
            .find(surveyCode, year)
            .map(RuleSet::tables)
            .map(tables -> tables.get(chapter.code()))
            .orElse(chapter.tableName());
    if (table == null) {
      table = chapter.tableName();
    }
    requireValid(chapter, table);
    validated.put(key, table);
    return table;
  }

  /** La misma resolución para la encuesta y el año activos. */
  public String activeTableFor(Chapter chapter) {
    return tableFor(chapter, survey.code(), survey.year());
  }

  /** Descarta lo validado: tras cambiar un mapeo o las tablas del esquema. */
  public void evict() {
    validated.clear();
  }

  /** Las mismas reglas de {@link #tableFor}, pero distingue la tabla que aún no existe. */
  public TableCheck check(String table) {
    if (table == null || !TABLE.matcher(table).matches()) {
      return TableCheck.INVALID_NAME;
    }
    if (hasRequiredColumns(table)) {
      return TableCheck.VALID;
    }
    Integer found =
        jdbc.queryForObject(
            EXISTS, Map.of("owner", survey.dbSchema(), "tableName", table), Integer.class);
    return found != null && found > 0 ? TableCheck.INCOMPLETE : TableCheck.MISSING;
  }

  private void requireValid(Chapter chapter, String table) {
    if (!TABLE.matcher(table).matches() || !hasRequiredColumns(table)) {
      // El nombre rechazado no se refleja: es texto del archivo y no debe volver al cliente.
      throw new AnswerValidationException(
          Map.of(
              "tablas." + chapter.code(),
              "La tabla indicada no existe o no tiene las columnas C_C, ESTADO y TC."));
    }
  }

  private boolean hasRequiredColumns(String table) {
    Integer found =
        jdbc.queryForObject(
            HAS_REQUIRED_COLUMNS,
            Map.of("owner", survey.dbSchema(), "tableName", table),
            Integer.class);
    return found != null && found == REQUIRED_COLUMNS;
  }
}
