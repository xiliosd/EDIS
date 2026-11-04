package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.AnswerTableResolver.ResolvedTable;
import co.gov.dane.edi.admin.AnswerTableResolver.TableCheck;
import co.gov.dane.edi.admin.dto.RuleQuestion;
import co.gov.dane.edi.model.AnswerColumn;
import co.gov.dane.edi.model.AnswerOption;
import co.gov.dane.edi.model.AnswerSpec;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.EntryKind;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.rules.LegacySkipTranslator;
import co.gov.dane.edi.rules.QuestionFacts;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.QuestionnaireService.PageRow;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Lo que el analizador y el editor de reglas necesitan saber de las preguntas de un año: página,
 * orden, tipo, opciones, estado de la columna y su capacidad en Oracle. Lee las filas completas
 * (con las preguntas aún sin columna) y, en un borrador, el diccionario de Oracle fresco, porque la
 * persona técnica crea las columnas mientras se edita.
 */
@Service
public class QuestionFactsService {

  private final YearCatalogService years;
  private final QuestionnaireService questionnaire;
  private final AnswerTableResolver tables;
  private final AnswerColumnRepository answerColumns;

  public QuestionFactsService(
      YearCatalogService years,
      QuestionnaireService questionnaire,
      AnswerTableResolver tables,
      AnswerColumnRepository answerColumns) {
    this.years = years;
    this.questionnaire = questionnaire;
    this.tables = tables;
    this.answerColumns = answerColumns;
  }

  /** Las preguntas del año y los datos para mostrarlas, leídos una sola vez. */
  public record Snapshot(Map<String, QuestionFacts> facts, List<RuleQuestion> questions) {}

  /**
   * Las preguntas del año según las reglas de un paquete: sus hechos y los saltos heredados de
   * FRM_SALTO, como los vería el encuestado si el paquete se importara.
   */
  public record PackageFacts(Map<String, QuestionFacts> facts, List<SkipRule> legacySkips) {}

  /** Solo los ítems, en el orden de las páginas y, dentro de cada una, el de la pregunta. */
  public Map<String, QuestionFacts> facts(String survey, int year) {
    return snapshot(survey, year).facts();
  }

  public Snapshot snapshot(String survey, int year) {
    boolean draft = years.requireYear(survey, year).effectiveState() == YearState.BORRADOR;
    Map<Chapter, Map<String, AnswerColumn>> columnsByChapter = new EnumMap<>(Chapter.class);
    Snapshot snapshot = new Snapshot(new LinkedHashMap<>(), new ArrayList<>());
    int position = 0;
    for (SurveyPage page : SurveyPage.ordered()) {
      Map<String, AnswerColumn> columns =
          columnsByChapter.computeIfAbsent(
              page.chapter(), chapter -> columnsOf(chapter, survey, year, draft));
      position = add(snapshot, page, questionnaire.pageRows(page, survey, year), columns, position);
    }
    return snapshot;
  }

  /**
   * Los hechos del año con el mapeo de tablas de {@code rules} y no con el archivo de este
   * ambiente, que puede no existir o ser anterior: la importación de un paquete revisa así las
   * reglas que trae. El diccionario se lee fresco porque {@code columnas.sql} acaba de correr; una
   * tabla del mapeo que no es válida deja pendientes todas sus preguntas.
   */
  public PackageFacts packageFacts(String survey, int year, RuleSet rules) {
    Map<Chapter, Map<String, AnswerColumn>> columnsByChapter = new EnumMap<>(Chapter.class);
    Snapshot snapshot = new Snapshot(new LinkedHashMap<>(), new ArrayList<>());
    List<SkipRule> legacy = new ArrayList<>();
    int position = 0;
    for (SurveyPage page : SurveyPage.ordered()) {
      Map<String, AnswerColumn> columns =
          columnsByChapter.computeIfAbsent(page.chapter(), chapter -> columnsOf(rules, chapter));
      List<PageRow> rows = questionnaire.pageRows(page, survey, year, columns.keySet());
      position = add(snapshot, page, rows, columns, position);
      // El encuestado no ve las preguntas sin columna: sus saltos heredados tampoco aplican.
      legacy.addAll(
          LegacySkipTranslator.translate(
              rows.stream().map(PageRow::entry).filter(entry -> !isPending(entry)).toList()));
    }
    return new PackageFacts(snapshot.facts(), legacy);
  }

  /** Agrega los ítems de la página y devuelve la posición siguiente en el año. */
  private static int add(
      Snapshot snapshot,
      SurveyPage page,
      List<PageRow> rows,
      Map<String, AnswerColumn> columns,
      int firstPosition) {
    int position = firstPosition;
    for (PageRow row : rows) {
      QuestionnaireEntry entry = row.entry();
      AnswerSpec answer = entry.answer();
      if (entry.kind() != EntryKind.ITEM || answer == null || answer.columnCode() == null) {
        continue;
      }
      List<AnswerOption> options = answer.options() == null ? List.of() : answer.options();
      snapshot
          .facts()
          .put(
              answer.columnCode(),
              new QuestionFacts(
                  answer.columnCode(),
                  page.code(),
                  position++,
                  answer.inputType(),
                  options.stream().map(AnswerOption::value).toList(),
                  answer.columnStatus(),
                  columns.get(answer.columnCode())));
      snapshot
          .questions()
          .add(
              new RuleQuestion(
                  answer.columnCode(),
                  page.code(),
                  entry.numeral(),
                  entry.text(),
                  answer.inputType().name(),
                  options,
                  answer.columnStatus()));
    }
    return position;
  }

  private static boolean isPending(QuestionnaireEntry entry) {
    return entry.answer() != null && entry.answer().columnStatus() == ColumnStatus.PENDING_COLUMN;
  }

  private Map<String, AnswerColumn> columnsOf(RuleSet rules, Chapter chapter) {
    String table = PromotionPackageFormat.tableOf(rules, chapter);
    if (tables.check(table) != TableCheck.VALID) {
      return Map.of();
    }
    answerColumns.evict(table);
    return answerColumns.findColumnDetails(table);
  }

  private Map<String, AnswerColumn> columnsOf(
      Chapter chapter, String survey, int year, boolean draft) {
    ResolvedTable table = tables.resolve(chapter, survey, year);
    if (table.missing()) {
      return Map.of();
    }
    if (draft) {
      answerColumns.evict(table.name());
    }
    return answerColumns.findColumnDetails(table.name());
  }
}
