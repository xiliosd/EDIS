package co.gov.dane.edi.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChapterTest {

  @Test
  void mapsChaptersToLegacyTablesAndPages() {
    assertThat(Chapter.B.tableName()).isEqualTo("EDIN_20_CAP_B_UBICA");
    assertThat(Chapter.C.pageCodes()).containsExactly("C1", "C2");
    assertThat(Chapter.D.pageCodes()).containsExactly("D1", "D2");
    assertThat(Chapter.H.tableName()).isEqualTo("EDIN_20_CAP_H_PREV_PRACT");
  }

  @Test
  void findsChapterIgnoringCase() {
    assertThat(Chapter.fromCode("c")).contains(Chapter.C);
    assertThat(Chapter.fromCode("Z")).isEmpty();
    assertThat(Chapter.fromCode(null)).isEmpty();
  }

  @Test
  void summarizesWithModule() {
    assertThat(Chapter.E.toSummary())
        .isEqualTo(
            new ChapterSummary(
                "E", "Uso de la tecnología e información", "Desempeño institucional"));
  }
}
