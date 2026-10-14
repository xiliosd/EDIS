package co.gov.dane.edi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.gov.dane.edi.model.AnswerOption;
import co.gov.dane.edi.model.ChapterDetail;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.EntryKind;
import co.gov.dane.edi.model.HeaderKind;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SectionHeader;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.repository.QuestionnaireRepository;
import co.gov.dane.edi.repository.QuestionnaireRow;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestionnaireServiceTest {

  private QuestionnaireRepository questionnaire;
  private QuestionnaireService service;

  @BeforeEach
  void setUp() {
    questionnaire = mock(QuestionnaireRepository.class);
    AnswerColumnRepository answerColumns = mock(AnswerColumnRepository.class);
    when(answerColumns.findColumns("EDIN_20_CAP_B_UBICA")).thenReturn(Set.of("B02", "B06"));
    when(questionnaire.findRowsByPage(anyString())).thenReturn(List.of());
    when(questionnaire.findRowsByPage("B1"))
        .thenReturn(
            List.of(
                new QuestionnaireRow(
                    1, 101, "Nivel del empleo", "P", "B2.", null, null, null, null),
                new QuestionnaireRow(2, 102, "Nivel", "L", "B02*.", 10, 2, "3", 1),
                new QuestionnaireRow(
                    4, 104, "Nueva <b>sin</b> columna", "L", "B99*.", 10, null, null, 1)));
    when(questionnaire.findHeaders(anyCollection()))
        .thenReturn(
            Map.of(
                101L, List.of(new SectionHeader(HeaderKind.HEADING, "Ubicaci&oacute;n laboral"))));
    when(questionnaire.findOptions(anyCollection()))
        .thenReturn(Map.of(10, List.of(new AnswerOption("2", "T&eacute;cnico"))));
    service = new QuestionnaireService(questionnaire, answerColumns);
  }

  @Test
  void listsTheSevenChaptersInOrder() {
    assertThat(service.listChapters())
        .extracting("code")
        .containsExactly("B", "C", "D", "E", "F", "G", "H");
  }

  @Test
  void mapsStatementsWithSanitizedHeaders() {
    QuestionnaireEntry statement = service.getChapter("B", false).entries().get(0);
    assertThat(statement.kind()).isEqualTo(EntryKind.STATEMENT);
    assertThat(statement.answer()).isNull();
    assertThat(statement.headers())
        .extracting(SectionHeader::text)
        .containsExactly("Ubicación laboral");
  }

  @Test
  void mapsItemsWithActiveColumnOptionsAndLegacySkip() {
    QuestionnaireEntry item = service.getChapter("b", false).entries().get(1);
    assertThat(item.kind()).isEqualTo(EntryKind.ITEM);
    assertThat(item.label()).isEqualTo("B02");
    assertThat(item.answer().inputType()).isEqualTo(InputType.SINGLE_CHOICE);
    assertThat(item.answer().columnCode()).isEqualTo("B02");
    assertThat(item.answer().columnStatus()).isEqualTo(ColumnStatus.ACTIVE);
    assertThat(item.answer().options()).extracting(AnswerOption::label).containsExactly("Técnico");
    assertThat(item.legacySkip().count()).isEqualTo(2);
    assertThat(item.legacySkip().triggerValue()).isEqualTo("3");
  }

  @Test
  void hidesPendingColumnsFromRespondents() {
    ChapterDetail chapter = service.getChapter("B", false);
    assertThat(chapter.entries()).extracting(QuestionnaireEntry::id).containsExactly(101L, 102L);
  }

  @Test
  void showsPendingColumnsToQuestionnaireAdministrators() {
    QuestionnaireEntry pending = service.getChapter("B", true).entries().get(2);
    assertThat(pending.answer().columnStatus()).isEqualTo(ColumnStatus.PENDING_COLUMN);
    assertThat(pending.text()).isEqualTo("Nueva sin columna");
  }

  @Test
  void unknownChapterIsNotFound() {
    assertThatThrownBy(() -> service.getChapter("Z", false))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("El capítulo solicitado no existe.");
  }

  @Test
  void mapsLegacyObjectCodes() {
    assertThat(InputType.fromLegacyObjectCode(1)).isEqualTo(InputType.SINGLE_CHOICE);
    assertThat(InputType.fromLegacyObjectCode(2)).isEqualTo(InputType.CHECKBOX);
    assertThat(InputType.fromLegacyObjectCode(5)).isEqualTo(InputType.TEXT);
    assertThat(InputType.fromLegacyObjectCode(null)).isEqualTo(InputType.UNKNOWN);
  }
}
