package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.AnswerTableResolver.TableCheck;
import co.gov.dane.edi.admin.dto.TablesResult;
import co.gov.dane.edi.admin.dto.YearSummary;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.SaveLocks;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.InvalidEditException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Tabla de respuestas de cada capítulo de un año en borrador y el script para crearla (D3: la
 * aplicación nunca crea tablas). Un borrador puede apuntar a una tabla que aún no existe: se
 * informa como faltante y la persona técnica la crea con el script antes de publicar.
 */
@Service
public class AnswerTableMappingService {

  private static final String LOCK_KEY = "admin:frm";
  private static final String OPERATION = "editar-tablas";

  private final YearCatalogService years;
  private final RulesFileRepository rulesFiles;
  private final AnswerTableResolver tables;
  private final ColumnScriptService scripts;
  private final QuestionnaireService questionnaire;
  private final EffectiveRulesService effectiveRules;
  private final SaveLocks locks;

  public AnswerTableMappingService(
      YearCatalogService years,
      RulesFileRepository rulesFiles,
      AnswerTableResolver tables,
      ColumnScriptService scripts,
      QuestionnaireService questionnaire,
      EffectiveRulesService effectiveRules,
      SaveLocks locks) {
    this.years = years;
    this.rulesFiles = rulesFiles;
    this.tables = tables;
    this.scripts = scripts;
    this.questionnaire = questionnaire;
    this.effectiveRules = effectiveRules;
    this.locks = locks;
  }

  /** Reemplaza el mapeo del año; los mensajes nunca repiten el nombre recibido. */
  public TablesResult saveTables(
      String survey, int year, Map<String, String> requested, int version, String user) {
    years.requireDraft(survey, year);
    Map<String, String> mapping = new TreeMap<>();
    List<String> missing = new ArrayList<>();
    for (Map.Entry<String, String> entry : requested.entrySet()) {
      Chapter chapter =
          Chapter.fromCode(entry.getKey())
              .orElseThrow(
                  () -> new InvalidEditException("tables", "Uno de los capítulos no existe."));
      String field = "tables." + chapter.code();
      String table = entry.getValue() == null ? "" : entry.getValue().strip();
      if (table.isEmpty()) {
        throw new InvalidEditException(field, "Indique la tabla del capítulo.");
      }
      if (mapping.put(chapter.code(), table) != null) {
        throw new InvalidEditException("tables", "Un capítulo aparece repetido.");
      }
      TableCheck check = tables.check(table);
      if (check == TableCheck.INVALID_NAME) {
        throw new InvalidEditException(
            field,
            "El nombre de la tabla debe ir en mayúsculas, empezar con una letra y tener de 2 a 30"
                + " caracteres (letras, números o _).");
      }
      if (check == TableCheck.INCOMPLETE) {
        throw new InvalidEditException(
            field, "La tabla indicada no tiene las columnas C_C, ESTADO y TC.");
      }
      if (check == TableCheck.MISSING) {
        missing.add(chapter.code());
      }
    }
    return locks.withLock(
        LOCK_KEY,
        () -> {
          years.requireDraft(survey, year);
          RuleSet rules = years.requireYear(survey, year);
          if (rules.version() != version) {
            throw new ConflictException(
                "Otro usuario modificó el cuestionario. Recargue la página.");
          }
          RuleSet saved;
          try {
            saved =
                rulesFiles.save(
                    rules.withTables(mapping),
                    version,
                    user,
                    OPERATION,
                    changedChapters(rules.tables(), mapping));
          } finally {
            tables.evict();
            questionnaire.evict();
            effectiveRules.evict();
          }
          return new TablesResult(saved.version(), saved.tables(), List.copyOf(missing));
        });
  }

  /**
   * {@code CREATE TABLE <nueva> AS SELECT * FROM <tabla del año anterior> WHERE 1 = 0;} para la
   * tabla asignada al capítulo que aún no existe.
   *
   * @throws ResourceNotFoundException si el capítulo no tiene tabla nueva, ya existe o no hay año
   *     anterior
   */
  public String createTableScript(String survey, int year, String chapterCode) {
    RuleSet file = years.requireYear(survey, year);
    Chapter chapter =
        Chapter.fromCode(chapterCode)
            .orElseThrow(() -> new ResourceNotFoundException("El capítulo no existe."));
    String newTable = file.tables().get(chapter.code());
    if (newTable == null) {
      throw new ResourceNotFoundException("El capítulo no tiene una tabla nueva en ese año.");
    }
    TableCheck check = tables.check(newTable);
    if (check == TableCheck.INVALID_NAME) {
      throw new InvalidEditException(
          "tables." + chapter.code(), "El nombre de la tabla asignada no es válido.");
    }
    if (check != TableCheck.MISSING) {
      throw new ResourceNotFoundException("La tabla ya existe en el esquema.");
    }
    int previous =
        years.years(survey).stream()
            .map(YearSummary::year)
            .filter(candidate -> candidate < year)
            .max(Integer::compare)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No hay un año anterior del que copiar la estructura."));
    return scripts.createTableLike(newTable, tables.tableFor(chapter, survey, previous));
  }

  /** Capítulos cuyo mapeo cambia: los códigos que llegan a la auditoría. */
  private static List<String> changedChapters(
      Map<String, String> before, Map<String, String> after) {
    TreeSet<String> chapters = new TreeSet<>(before.keySet());
    chapters.addAll(after.keySet());
    chapters.removeIf(code -> Objects.equals(before.get(code), after.get(code)));
    return List.copyOf(chapters);
  }
}
