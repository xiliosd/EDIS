package co.gov.dane.edi.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SurveyPageTest {

  @Test
  void ordersPagesLikeTheLegacyMenu() {
    assertThat(SurveyPage.ordered())
        .extracting(SurveyPage::code)
        .containsExactly("B1", "C1", "C2", "D1", "D2", "E1", "F1", "G1", "H1");
  }

  @Test
  void findsPagesIgnoringCaseAndKnowsTheNextOne() {
    SurveyPage c2 = SurveyPage.find("c2").orElseThrow();
    assertThat(c2.chapter()).isEqualTo(Chapter.C);
    assertThat(c2.next()).map(SurveyPage::code).contains("D1");
    assertThat(SurveyPage.find("H1").orElseThrow().next()).isEmpty();
    assertThat(SurveyPage.find("Z9")).isEmpty();
    assertThat(SurveyPage.find(null)).isEmpty();
  }

  @Test
  void firstPageOfChapterInsertsTheRow() {
    assertThat(SurveyPage.find("C1").orElseThrow().isFirstOfChapter()).isTrue();
    assertThat(SurveyPage.find("C2").orElseThrow().isFirstOfChapter()).isFalse();
  }
}
