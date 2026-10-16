package co.gov.dane.edi.service;

import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.PageDetail;
import co.gov.dane.edi.model.PageState;
import co.gov.dane.edi.model.PageStatus;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.model.SurveyProgress;
import co.gov.dane.edi.repository.AnswerRepository;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class SurveyProgressService {

  private final QuestionnaireService questionnaire;
  private final AnswerRepository answers;
  private final ReceiptService receipts;

  public SurveyProgressService(
      QuestionnaireService questionnaire, AnswerRepository answers, ReceiptService receipts) {
    this.questionnaire = questionnaire;
    this.answers = answers;
    this.receipts = receipts;
  }

  public SurveyProgress progress(String respondentCode) {
    Map<SurveyPage, PageState> states = states(respondentCode);
    List<PageStatus> pages = new ArrayList<>();
    states.forEach(
        (page, state) ->
            pages.add(
                new PageStatus(
                    page.code(),
                    page.chapter().code(),
                    page.chapter().title(),
                    page.chapter().module(),
                    state)));
    boolean finished = states.values().stream().allMatch(state -> state == PageState.SAVED);
    boolean observationsSaved = finished && answers.findObservations(respondentCode) != null;
    return new SurveyProgress(
        pages, finished, observationsSaved, finished ? receipts.receiptFor(respondentCode) : null);
  }

  public PageDetail pageDetail(String respondentCode, String pageCode) {
    SurveyPage page =
        SurveyPage.find(pageCode)
            .orElseThrow(() -> new ResourceNotFoundException("La página solicitada no existe."));
    List<QuestionnaireEntry> entries = questionnaire.pageEntries(page);
    PageState state = states(respondentCode).get(page);
    Map<String, String> saved =
        state == PageState.SAVED
            ? answers.findAnswers(
                page.chapter(), respondentCode, QuestionnaireService.activeItemColumns(entries))
            : Map.of();
    Chapter chapter = page.chapter();
    return new PageDetail(
        page.code(), chapter.code(), chapter.title(), chapter.module(), state, entries, saved);
  }

  public PageState stateOf(String respondentCode, SurveyPage page) {
    return states(respondentCode).get(page);
  }

  /** Respuestas guardadas de todo el cuestionario: las reglas pueden mirar páginas anteriores. */
  public Map<String, String> allAnswers(String respondentCode) {
    Map<String, String> all = new HashMap<>();
    for (Chapter chapter : Chapter.values()) {
      List<String> columns =
          chapter.pageCodes().stream()
              .flatMap(
                  code ->
                      QuestionnaireService.activeItemColumns(
                          questionnaire.pageEntries(SurveyPage.find(code).orElseThrow()))
                          .stream())
              .toList();
      all.putAll(answers.findAnswers(chapter, respondentCode, columns));
    }
    return all;
  }

  private Map<SurveyPage, PageState> states(String respondentCode) {
    Map<SurveyPage, Boolean> saved = savedPages(respondentCode);
    Map<SurveyPage, PageState> states = new LinkedHashMap<>();
    boolean previousSaved = true;
    for (SurveyPage page : SurveyPage.ordered()) {
      boolean pageSaved = saved.get(page);
      PageState state =
          pageSaved ? PageState.SAVED : previousSaved ? PageState.AVAILABLE : PageState.LOCKED;
      states.put(page, state);
      previousSaved = previousSaved && pageSaved;
    }
    return states;
  }

  /** Una consulta por capítulo: las columnas del primer ítem activo de todas sus páginas. */
  private Map<SurveyPage, Boolean> savedPages(String respondentCode) {
    Map<SurveyPage, Boolean> saved = new HashMap<>();
    for (Chapter chapter : Chapter.values()) {
      Map<SurveyPage, String> firstColumns = new LinkedHashMap<>();
      for (SurveyPage page : SurveyPage.ordered()) {
        if (page.chapter() == chapter) {
          List<String> columns =
              QuestionnaireService.activeItemColumns(questionnaire.pageEntries(page));
          firstColumns.put(page, columns.isEmpty() ? null : columns.get(0));
        }
      }
      List<String> guards = firstColumns.values().stream().filter(Objects::nonNull).toList();
      Map<String, String> found = answers.findAnswers(chapter, respondentCode, guards);
      // Página sin preguntas activas: no hay nada que guardar, cuenta como guardada.
      firstColumns.forEach(
          (page, column) -> saved.put(page, column == null || found.containsKey(column)));
    }
    return saved;
  }
}
