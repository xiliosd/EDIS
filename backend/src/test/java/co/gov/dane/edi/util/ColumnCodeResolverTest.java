package co.gov.dane.edi.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ColumnCodeResolverTest {

  @Test
  void derivesColumnFromLegacyNumeral() {
    assertThat(ColumnCodeResolver.fromNumeral("C07A.")).contains("C07A");
    assertThat(ColumnCodeResolver.fromNumeral("B02*.")).contains("B02");
    assertThat(ColumnCodeResolver.fromNumeral(" e01b.  ")).contains("E01B");
  }

  @Test
  void rejectsNumeralsThatAreNotValidColumns() {
    assertThat(ColumnCodeResolver.fromNumeral("B2-2025.")).isEmpty();
    assertThat(ColumnCodeResolver.fromNumeral("1A.")).isEmpty();
    assertThat(ColumnCodeResolver.fromNumeral("B02; DROP")).isEmpty();
    assertThat(ColumnCodeResolver.fromNumeral(null)).isEmpty();
  }

  @Test
  void buildsLegacyDisplayLabel() {
    assertThat(ColumnCodeResolver.displayLabel("C01A.")).isEqualTo("A.");
    assertThat(ColumnCodeResolver.displayLabel("B02*.")).isEqualTo("B02");
    assertThat(ColumnCodeResolver.displayLabel("E2.   ")).isEqualTo("2.");
  }
}
