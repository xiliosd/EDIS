package co.gov.dane.edi.service;

import co.gov.dane.edi.model.AnswerColumn;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.EntryKind;
import co.gov.dane.edi.model.FinishResult;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.model.PageSaveResult;
import co.gov.dane.edi.model.PageState;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.repository.AnswerRepository;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.Evaluation;
import co.gov.dane.edi.rules.RuleErrorCodes;
import co.gov.dane.edi.rules.RuleEvaluator;
import co.gov.dane.edi.service.error.AnswerValidationException;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SurveyAnswerService {

  static final String SAVED_MESSAGE = "Esta página ya fue guardada.";
  static final String LOCKED_MESSAGE = "Complete primero las páginas anteriores.";
  // Valor que el PHP heredado guarda para preguntas deshabilitadas o sin respuesta (D15).
  private static final String EMPTY_VALUE = "0";
  static final String OBSERVATIONS_TOO_LONG_MESSAGE =
      "Las observaciones superan el espacio disponible. Acórtelas.";
  static final String MISSING_PAGES_MESSAGE = "Faltan páginas por guardar.";
  private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
  private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

  private final SurveyProgressService progress;
  private final QuestionnaireService questionnaire;
  private final EffectiveRulesService rules;
  private final AnswerRepository answers;
  private final AnswerColumnRepository answerColumns;
  private final SaveLocks locks;
  private final ReceiptService receipts;

  public SurveyAnswerService(
      SurveyProgressService progress,
      QuestionnaireService questionnaire,
      EffectiveRulesService rules,
      AnswerRepository answers,
      AnswerColumnRepository answerColumns,
      SaveLocks locks,
      ReceiptService receipts) {
    this.progress = progress;
    this.questionnaire = questionnaire;
    this.rules = rules;
    this.answers = answers;
    this.answerColumns = answerColumns;
    this.locks = locks;
    this.receipts = receipts;
  }

  public PageSaveResult savePage(
      String respondentCode, String pageCode, Map<String, String> submitted) {
    SurveyPage page =
        SurveyPage.find(pageCode)
            .orElseThrow(() -> new ResourceNotFoundException("La página solicitada no existe."));
    Map<String, String> safeSubmitted = submitted == null ? Map.of() : submitted;
    return locks.withLock(respondentCode, () -> save(respondentCode, page, safeSubmitted));
  }

  /** Termina la encuesta: exige las 9 páginas, guarda OBS una sola vez y devuelve el radicado. */
  public FinishResult finish(String respondentCode, String observations) {
    return locks.withLock(respondentCode, () -> doFinish(respondentCode, observations));
  }

  private FinishResult doFinish(String respondentCode, String observations) {
    boolean allSaved =
        SurveyPage.ordered().stream()
            .allMatch(page -> progress.stateOf(respondentCode, page) == PageState.SAVED);
    if (!allSaved) {
      throw new ConflictException(MISSING_PAGES_MESSAGE);
    }
    String text =
        observations == null
            ? ""
            : CONTROL_CHARACTERS.matcher(observations.replace("\r\n", "\n")).replaceAll("").strip();
    // OBS se escribe una sola vez (D18): repetir la llamada no la modifica.
    if (!text.isEmpty() && answers.findObservations(respondentCode) == null) {
      // @Size cuenta unidades UTF-16, pero OBS puede medirse en bytes: se evita el ORA-12899.
      AnswerColumn observationsColumn =
          answerColumns.findColumnDetails(Chapter.H.tableName()).get("OBS");
      if (observationsColumn != null && checkColumn(text, observationsColumn).isPresent()) {
        throw new AnswerValidationException(Map.of("observations", OBSERVATIONS_TOO_LONG_MESSAGE));
      }
      answers.saveObservations(respondentCode, text);
    }
    return new FinishResult(receipts.receiptFor(respondentCode));
  }

  private PageSaveResult save(
      String respondentCode, SurveyPage page, Map<String, String> submitted) {
    PageState state = progress.stateOf(respondentCode, page);
    if (state == PageState.SAVED) {
      throw new ConflictException(SAVED_MESSAGE);
    }
    if (state == PageState.LOCKED) {
      throw new ConflictException(LOCKED_MESSAGE);
    }
    List<QuestionnaireEntry> entries = questionnaire.pageEntries(page);
    List<String> items = QuestionnaireService.activeItemColumns(entries);
    Map<String, String> errors = new TreeMap<>();
    submitted.keySet().stream()
        .filter(key -> !items.contains(key))
        .forEach(key -> errors.put(key, RuleErrorCodes.UNKNOWN_QUESTION));

    Map<String, String> combined = new HashMap<>(progress.allAnswers(respondentCode));
    items.forEach(column -> combined.remove(column));
    submitted.forEach(
        (key, value) -> {
          if (items.contains(key) && value != null) {
            combined.put(key, value);
          }
        });
    Evaluation evaluation = RuleEvaluator.evaluate(rules.effectiveRules(), combined, items);
    errors.putAll(evaluation.errors());
    Map<String, AnswerColumn> columns = answerColumns.findColumnDetails(page.chapter().tableName());
    for (QuestionnaireEntry entry : entries) {
      if (entry.kind() != EntryKind.ITEM || !items.contains(entry.answer().columnCode())) {
        continue;
      }
      String column = entry.answer().columnCode();
      String value = combined.get(column);
      if (evaluation.disabled().contains(column)
          || value == null
          || value.isBlank()
          || errors.containsKey(column)) {
        continue;
      }
      checkValue(entry, value.strip(), columns.get(column))
          .ifPresent(code -> errors.put(column, code));
    }
    if (!errors.isEmpty()) {
      Map<String, String> messages = new TreeMap<>();
      errors.forEach((column, code) -> messages.put(column, AnswerErrorMessages.messageFor(code)));
      throw new AnswerValidationException(messages);
    }

    Map<String, String> values = new LinkedHashMap<>();
    for (String column : items) {
      String value = combined.get(column);
      boolean empty = evaluation.disabled().contains(column) || value == null || value.isBlank();
      values.put(column, empty ? EMPTY_VALUE : value.strip());
    }
    write(respondentCode, page, values, items);
    return new PageSaveResult(page.code(), page.next().map(SurveyPage::code).orElse(null));
  }

  private void write(
      String respondentCode, SurveyPage page, Map<String, String> values, List<String> items) {
    if (items.isEmpty()) {
      return;
    }
    if (!answers.rowExists(page.chapter(), respondentCode)) {
      answers.insertPage(page.chapter(), respondentCode, values);
      return;
    }
    if (answers.updatePage(page.chapter(), respondentCode, values, items.get(0)) == 0) {
      throw new ConflictException(SAVED_MESSAGE);
    }
  }

  private static Optional<String> checkValue(
      QuestionnaireEntry entry, String value, AnswerColumn column) {
    InputType type = entry.answer().inputType();
    if (type == InputType.SINGLE_CHOICE
        && !entry.answer().options().isEmpty()
        && entry.answer().options().stream().noneMatch(option -> option.value().equals(value))) {
      return Optional.of(RuleErrorCodes.INVALID_OPTION);
    }
    if (type == InputType.CHECKBOX && !value.equals("1") && !value.equals(EMPTY_VALUE)) {
      return Optional.of(RuleErrorCodes.INVALID_OPTION);
    }
    return column == null ? Optional.empty() : checkColumn(value, column);
  }

  /** Rechaza lo que Oracle no aceptaría o redondearía (ORA-12899, ORA-01438). */
  static Optional<String> checkColumn(String value, AnswerColumn column) {
    if (!"NUMBER".equals(column.dataType())) {
      // Sin CHAR_USED = 'C' la longitud es en bytes: "ññ" ocupa 4 en AL32UTF8.
      boolean tooLong =
          column.charSemantics()
              ? value.codePointCount(0, value.length()) > column.charLength()
              : value.getBytes(StandardCharsets.UTF_8).length > column.dataLength();
      return tooLong ? Optional.of(RuleErrorCodes.TOO_LONG) : Optional.empty();
    }
    if (!NUMBER.matcher(value).matches()) {
      return Optional.of(RuleErrorCodes.INVALID_TYPE);
    }
    if (column.dataPrecision() == null && column.dataScale() == null) {
      return Optional.empty();
    }
    int scale = column.dataScale() == null ? 0 : column.dataScale();
    String unsigned = value.startsWith("-") ? value.substring(1) : value;
    int point = unsigned.indexOf('.');
    String integerPart = point < 0 ? unsigned : unsigned.substring(0, point);
    String decimalPart = point < 0 ? "" : unsigned.substring(point + 1);
    // Oracle redondearía los decimales sobrantes en silencio: se piden como dato inválido.
    if (decimalPart.replaceFirst("0+$", "").length() > Math.max(scale, 0)) {
      return Optional.of(RuleErrorCodes.INVALID_TYPE);
    }
    String significantInteger = integerPart.replaceFirst("^0+", "");
    if (column.dataPrecision() != null
        && significantInteger.length() > column.dataPrecision() - scale) {
      return Optional.of(RuleErrorCodes.OUT_OF_RANGE);
    }
    return Optional.empty();
  }
}
