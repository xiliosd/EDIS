package co.gov.dane.edi.service;

import co.gov.dane.edi.admin.AnswerTableResolver;
import co.gov.dane.edi.admin.AnswerTableResolver.ResolvedTable;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.AnswerOption;
import co.gov.dane.edi.model.AnswerSpec;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.ChapterDetail;
import co.gov.dane.edi.model.ChapterSummary;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.EntryKind;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.model.LegacySkip;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SectionHeader;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.repository.QuestionnaireRepository;
import co.gov.dane.edi.repository.QuestionnaireRow;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import co.gov.dane.edi.util.ColumnCodeResolver;
import co.gov.dane.edi.util.QuestionTextSanitizer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class QuestionnaireService {

  private static final String STATEMENT_TYPE = "P";

  private final QuestionnaireRepository questionnaire;
  private final AnswerColumnRepository answerColumns;
  private final AnswerTableResolver tables;
  private final SurveyProperties survey;

  /**
   * Preguntas por página en memoria: cada consulta del encuestado las necesita varias veces. Los
   * cambios en las tablas FRM exigen reiniciar hasta que el editor de la Fase 3 llame a evict().
   */
  private final Map<PageKey, List<QuestionnaireEntry>> entriesByPage = new ConcurrentHashMap<>();

  private final AtomicLong cacheGeneration = new AtomicLong();

  private record PageKey(SurveyPage page, String survey, int year) {}

  /** Fila de FRM_* junto con la pregunta ya armada: el editor necesita ambas. */
  public record PageRow(QuestionnaireRow row, QuestionnaireEntry entry) {}

  public QuestionnaireService(
      QuestionnaireRepository questionnaire,
      AnswerColumnRepository answerColumns,
      AnswerTableResolver tables,
      SurveyProperties survey) {
    this.questionnaire = questionnaire;
    this.answerColumns = answerColumns;
    this.tables = tables;
    this.survey = survey;
  }

  public List<ChapterSummary> listChapters() {
    return Arrays.stream(Chapter.values()).map(Chapter::toSummary).toList();
  }

  public ChapterDetail getChapter(String chapterCode, boolean includePendingColumns) {
    Chapter chapter =
        Chapter.fromCode(chapterCode)
            .orElseThrow(() -> new ResourceNotFoundException("El capítulo solicitado no existe."));
    Set<String> columns = answerColumns.findColumns(tables.activeTableFor(chapter));
    List<QuestionnaireEntry> entries =
        chapter.pageCodes().stream()
            .flatMap(
                page ->
                    buildEntries(page, columns, includePendingColumns, survey.code(), survey.year())
                        .stream()
                        .map(PageRow::entry))
            .toList();
    return new ChapterDetail(chapter.code(), chapter.title(), chapter.module(), entries);
  }

  /** Preguntas que ve el encuestado en una página, sin columnas pendientes de crear. */
  public List<QuestionnaireEntry> pageEntries(SurveyPage page) {
    return pageEntries(page, survey.code(), survey.year());
  }

  /** Lo mismo para cualquier encuesta y año, en memoria por (página, encuesta, año). */
  public List<QuestionnaireEntry> pageEntries(SurveyPage page, String surveyCode, int year) {
    return entriesByPage.computeIfAbsent(
        new PageKey(page, surveyCode, year),
        key ->
            buildEntries(
                    page.code(),
                    columnsOf(page.chapter(), surveyCode, year),
                    false,
                    surveyCode,
                    year)
                .stream()
                .map(PageRow::entry)
                .toList());
  }

  /**
   * Filas de la página para el administrador: incluye las preguntas sin columna y el grupo de
   * opciones. No se guarda en memoria porque el editor debe ver siempre lo último.
   */
  public List<PageRow> pageRows(SurveyPage page, String surveyCode, int year) {
    return buildEntries(
        page.code(), columnsOf(page.chapter(), surveyCode, year), true, surveyCode, year);
  }

  /** Sin columnas si la tabla de un año en borrador aún no existe: todo queda pendiente. */
  private Set<String> columnsOf(Chapter chapter, String surveyCode, int year) {
    ResolvedTable table = tables.resolve(chapter, surveyCode, year);
    return table.missing() ? Set.of() : answerColumns.findColumns(table.name());
  }

  /** Descarta las preguntas en memoria; las cachés derivadas comparan la generación. */
  public void evict() {
    entriesByPage.clear();
    cacheGeneration.incrementAndGet();
  }

  /** Cambia en cada evict(): permite a EffectiveRulesService invalidar su copia a la vez. */
  public long cacheGeneration() {
    return cacheGeneration.get();
  }

  /** Columnas de respuesta de los ítems activos, en el orden de la página. */
  public static List<String> activeItemColumns(List<QuestionnaireEntry> entries) {
    return entries.stream()
        .filter(
            e ->
                e.kind() == EntryKind.ITEM
                    && e.answer() != null
                    && e.answer().columnStatus() == ColumnStatus.ACTIVE)
        .map(e -> e.answer().columnCode())
        .toList();
  }

  /** El año activo conserva las consultas de siempre; otro año usa las que reciben parámetros. */
  private List<PageRow> buildEntries(
      String pageCode,
      Set<String> columns,
      boolean includePendingColumns,
      String surveyCode,
      int year) {
    boolean active = surveyCode.equals(survey.code()) && year == survey.year();
    List<QuestionnaireRow> rows =
        active
            ? questionnaire.findRowsByPage(pageCode)
            : questionnaire.findRowsByPage(pageCode, surveyCode, String.valueOf(year));
    List<Long> entryIds = rows.stream().map(QuestionnaireRow::entryId).toList();
    Map<Long, List<SectionHeader>> headers =
        active
            ? questionnaire.findHeaders(entryIds)
            : questionnaire.findHeaders(entryIds, String.valueOf(year));
    Map<Integer, List<AnswerOption>> options =
        questionnaire.findOptions(
            rows.stream()
                .map(QuestionnaireRow::optionGroup)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    return rows.stream()
        .map(row -> new PageRow(row, toEntry(pageCode, row, headers, options, columns)))
        .filter(built -> includePendingColumns || !isPending(built.entry()))
        .toList();
  }

  private static boolean isPending(QuestionnaireEntry entry) {
    return entry.answer() != null && entry.answer().columnStatus() == ColumnStatus.PENDING_COLUMN;
  }

  private static QuestionnaireEntry toEntry(
      String pageCode,
      QuestionnaireRow row,
      Map<Long, List<SectionHeader>> headers,
      Map<Integer, List<AnswerOption>> options,
      Set<String> columns) {
    String numeral = row.numeral() == null ? "" : row.numeral().strip();
    List<SectionHeader> entryHeaders =
        headers.getOrDefault(row.entryId(), List.of()).stream()
            .map(h -> new SectionHeader(h.kind(), QuestionTextSanitizer.toPlainText(h.text())))
            .toList();
    String text = QuestionTextSanitizer.toPlainText(row.text());
    if (STATEMENT_TYPE.equals(row.type())) {
      return new QuestionnaireEntry(
          row.entryId(),
          pageCode,
          EntryKind.STATEMENT,
          numeral,
          numeral,
          text,
          entryHeaders,
          null,
          null);
    }
    Optional<String> column = ColumnCodeResolver.fromNumeral(numeral);
    ColumnStatus status =
        column.filter(columns::contains).isPresent()
            ? ColumnStatus.ACTIVE
            : ColumnStatus.PENDING_COLUMN;
    List<AnswerOption> entryOptions =
        options.getOrDefault(row.optionGroup(), List.of()).stream()
            .map(o -> new AnswerOption(o.value(), QuestionTextSanitizer.toPlainText(o.label())))
            .toList();
    AnswerSpec answer =
        new AnswerSpec(
            InputType.fromLegacyObjectCode(row.objectCode()),
            column.orElse(null),
            status,
            entryOptions);
    LegacySkip legacySkip =
        row.legacySkipCount() == null || row.legacySkipCount() == 0
            ? null
            : new LegacySkip(row.legacySkipCount(), row.legacySkipValue());
    return new QuestionnaireEntry(
        row.entryId(),
        pageCode,
        EntryKind.ITEM,
        numeral,
        ColumnCodeResolver.displayLabel(numeral),
        text,
        entryHeaders,
        answer,
        legacySkip);
  }
}
