package co.gov.dane.edi.operation.report;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.operation.report.dto.CoverageTotals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CoverageTotalsTest {

  @Test
  void computesEffectiveSampleAndPercentages() {
    CoverageTotals totals = CoverageTotals.of(10, 2, 5, 1);

    assertThat(totals.effectiveSample()).isEqualTo(8);
    assertThat(totals.notStarted()).isEqualTo(2);
    assertThat(totals.completePct()).isEqualByComparingTo("62.50");
    assertThat(totals.incompletePct()).isEqualByComparingTo("12.50");
    assertThat(totals.notStartedPct()).isEqualByComparingTo("25.00");
    assertThat(totals.completePct().scale()).isEqualTo(2);
  }

  @Test
  void everyoneWithNoveltyGivesZeroEffectiveSampleAndZeroPercentages() {
    CoverageTotals totals = CoverageTotals.of(3, 3, 0, 0);

    assertThat(totals.effectiveSample()).isZero();
    assertThat(totals.completePct()).isEqualTo(new BigDecimal("0.00"));
    assertThat(totals.incompletePct()).isEqualTo(new BigDecimal("0.00"));
    assertThat(totals.notStartedPct()).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void emptySampleIsAllZeros() {
    CoverageTotals totals = CoverageTotals.of(0, 0, 0, 0);

    assertThat(totals.sample()).isZero();
    assertThat(totals.notStarted()).isZero();
    assertThat(totals.completePct()).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void roundsHalfUpToTwoDecimals() {
    CoverageTotals totals = CoverageTotals.of(7, 0, 2, 2);

    assertThat(totals.completePct()).isEqualByComparingTo("28.57");
    assertThat(totals.notStartedPct()).isEqualByComparingTo("42.86");
    assertThat(CoverageTotals.of(8, 0, 1, 0).completePct()).isEqualByComparingTo("12.50");
    assertThat(CoverageTotals.of(3, 0, 1, 0).completePct()).isEqualByComparingTo("33.33");
    assertThat(CoverageTotals.of(8, 0, 0, 1).incompletePct()).isEqualByComparingTo("12.50");
    assertThat(CoverageTotals.of(16, 0, 1, 0).completePct()).isEqualByComparingTo("6.25");
    assertThat(CoverageTotals.of(800, 0, 1, 0).completePct()).isEqualByComparingTo("0.13");
  }

  @Test
  void plusAddsCountsAndRecomputesPercentages() {
    CoverageTotals sum = CoverageTotals.of(10, 2, 5, 1).plus(CoverageTotals.of(4, 0, 1, 1));

    assertThat(sum.sample()).isEqualTo(14);
    assertThat(sum.novelties()).isEqualTo(2);
    assertThat(sum.effectiveSample()).isEqualTo(12);
    assertThat(sum.complete()).isEqualTo(6);
    assertThat(sum.incomplete()).isEqualTo(2);
    assertThat(sum.notStarted()).isEqualTo(4);
    assertThat(sum.completePct()).isEqualByComparingTo("50.00");
  }
}
