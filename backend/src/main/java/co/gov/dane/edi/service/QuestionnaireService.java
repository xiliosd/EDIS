package co.gov.dane.edi.service;

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
import org.springframework.stereotype.Service;

@Service
public class QuestionnaireService {

  private static final String STATEMENT_TYPE = "P";

  private final QuestionnaireRepository questionnaire;
  private final AnswerColumnRepository answerColumns;

  public QuestionnaireService(
      QuestionnaireRepository questionnaire, AnswerColumnRepository answerColumns) {
    this.questionnaire = questionnaire;
    this.answerColumns = answerColumns;
  }

  public List<ChapterSummary> listChapters() {
    return Arrays.stream(Chapter.values()).map(Chapter::toSummary).toList();
  }

  public ChapterDetail getChapter(String chapterCode, boolean includePendingColumns) {
    Chapter chapter =
        Chapter.fromCode(chapterCode)
            .orElseThrow(() -> new ResourceNotFoundException("El capítulo solicitado no existe."));
    Set<String> columns = answerColumns.findColumns(chapter.tableName());
    List<QuestionnaireEntry> entries =
        chapter.pageCodes().stream()
            .flatMap(page -> buildEntries(page, columns, includePendingColumns).stream())
            .toList();
    return new ChapterDetail(chapter.code(), chapter.title(), chapter.module(), entries);
  }

  /** Preguntas que ve el encuestado en una página, sin columnas pendientes de crear. */
  public List<QuestionnaireEntry> pageEntries(SurveyPage page) {
    Set<String> columns = answerColumns.findColumns(page.chapter().tableName());
    return buildEntries(page.code(), columns, false);
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

  private List<QuestionnaireEntry> buildEntries(
      String pageCode, Set<String> columns, boolean includePendingColumns) {
    List<QuestionnaireRow> rows = questionnaire.findRowsByPage(pageCode);
    Map<Long, List<SectionHeader>> headers =
        questionnaire.findHeaders(rows.stream().map(QuestionnaireRow::entryId).toList());
    Map<Integer, List<AnswerOption>> options =
        questionnaire.findOptions(
            rows.stream()
                .map(QuestionnaireRow::optionGroup)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    return rows.stream()
        .map(row -> toEntry(pageCode, row, headers, options, columns))
        .filter(entry -> includePendingColumns || !isPending(entry))
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
