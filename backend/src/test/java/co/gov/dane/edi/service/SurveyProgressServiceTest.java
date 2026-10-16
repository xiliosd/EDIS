package co.gov.dane.edi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.gov.dane.edi.model.AnswerSpec;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.EntryKind;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.model.PageState;
import co.gov.dane.edi.model.PageStatus;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SurveyProgressServiceTest {

  private static final String CODE = "9000000999";

  private static QuestionnaireEntry item(String pageCode, String column) {
    return new QuestionnaireEntry(
        1,
        pageCode,
        EntryKind.ITEM,
        column + ".",
        column,
        "Pregunta ficticia",
        List.of(),
        new AnswerSpec(InputType.SINGLE_CHOICE, column, ColumnStatus.ACTIVE, List.of()),
        null);
  }

  @Test
  void readsTheSavedStateWithOneQueryPerChapter() {
    QuestionnaireService questionnaire = mock(QuestionnaireService.class);
    AnswerRepository answers = mock(AnswerRepository.class);
    for (SurveyPage page : SurveyPage.ordered()) {
      when(questionnaire.pageEntries(page)).thenReturn(List.of(item(page.code(), page.code())));
    }
    when(answers.findAnswers(eq(Chapter.B), eq(CODE), anyCollection()))
        .thenReturn(Map.of("B1", "1"));
    when(answers.findAnswers(eq(Chapter.C), eq(CODE), anyCollection()))
        .thenReturn(Map.of("C1", "1"));
    for (Chapter chapter : List.of(Chapter.D, Chapter.E, Chapter.F, Chapter.G, Chapter.H)) {
      when(answers.findAnswers(eq(chapter), eq(CODE), anyCollection())).thenReturn(Map.of());
    }
    SurveyProgressService service =
        new SurveyProgressService(questionnaire, answers, mock(ReceiptService.class));

    List<PageStatus> pages = service.progress(CODE).pages();

    assertThat(pages)
        .extracting(PageStatus::state)
        .containsExactly(
            PageState.SAVED,
            PageState.SAVED,
            PageState.AVAILABLE,
            PageState.LOCKED,
            PageState.LOCKED,
            PageState.LOCKED,
            PageState.LOCKED,
            PageState.LOCKED,
            PageState.LOCKED);
    verify(answers).findAnswers(Chapter.C, CODE, List.of("C1", "C2"));
    for (Chapter chapter : Chapter.values()) {
      verify(answers, times(1)).findAnswers(eq(chapter), eq(CODE), anyCollection());
    }
  }
}
