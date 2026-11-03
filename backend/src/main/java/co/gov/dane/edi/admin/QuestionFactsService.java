package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.AnswerTableResolver.ResolvedTable;
import co.gov.dane.edi.admin.dto.RuleQuestion;
import co.gov.dane.edi.model.AnswerColumn;
import co.gov.dane.edi.model.AnswerOption;
import co.gov.dane.edi.model.AnswerSpec;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.EntryKind;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.rules.QuestionFacts;
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

  /** Solo los ítems, en el orden de las páginas y, dentro de cada una, el de la pregunta. */
  public Map<String, QuestionFacts> facts(String survey, int year) {
    return snapshot(survey, year).facts();
  }

  public Snapshot snapshot(String survey, int year) {
    boolean draft = years.requireYear(survey, year).effectiveState() == YearState.BORRADOR;
    Map<Chapter, Map<String, AnswerColumn>> columnsByChapter = new EnumMap<>(Chapter.class);
    Map<String, QuestionFacts> facts = new LinkedHashMap<>();
    List<RuleQuestion> questions = new ArrayList<>();
    int position = 0;
    for (SurveyPage page : SurveyPage.ordered()) {
      Map<String, AnswerColumn> columns =
          columnsByChapter.computeIfAbsent(
              page.chapter(), chapter -> columnsOf(chapter, survey, year, draft));
      for (PageRow row : questionnaire.pageRows(page, survey, year)) {
        QuestionnaireEntry entry = row.entry();
        AnswerSpec answer = entry.answer();
        if (entry.kind() != EntryKind.ITEM || answer == null || answer.columnCode() == null) {
          continue;
        }
        List<AnswerOption> options = answer.options() == null ? List.of() : answer.options();
        facts.put(
            answer.columnCode(),
            new QuestionFacts(
                answer.columnCode(),
                page.code(),
                position++,
                answer.inputType(),
                options.stream().map(AnswerOption::value).toList(),
                answer.columnStatus(),
                columns.get(answer.columnCode())));
        questions.add(
            new RuleQuestion(
                answer.columnCode(),
                page.code(),
                entry.numeral(),
                entry.text(),
                answer.inputType().name(),
                options,
                answer.columnStatus()));
      }
    }
    return new Snapshot(facts, questions);
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
