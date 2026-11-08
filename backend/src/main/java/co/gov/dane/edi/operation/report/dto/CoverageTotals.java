package co.gov.dane.edi.operation.report.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cifras de cobertura; los porcentajes son sobre la muestra efectiva (muestra - novedades). */
public record CoverageTotals(
    long sample,
    long novelties,
    long effectiveSample,
    long complete,
    long incomplete,
    long notStarted,
    BigDecimal completePct,
    BigDecimal incompletePct,
    BigDecimal notStartedPct) {

  private static final int PERCENT_SCALE = 2;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  public static CoverageTotals of(long sample, long novelties, long complete, long incomplete) {
    long effective = sample - novelties;
    long notStarted = effective - complete - incomplete;
    return new CoverageTotals(
        sample,
        novelties,
        effective,
        complete,
        incomplete,
        notStarted,
        percent(complete, effective),
        percent(incomplete, effective),
        percent(notStarted, effective));
  }

  public CoverageTotals plus(CoverageTotals other) {
    return of(
        sample + other.sample,
        novelties + other.novelties,
        complete + other.complete,
        incomplete + other.incomplete);
  }

  private static BigDecimal percent(long part, long whole) {
    if (whole == 0) {
      return BigDecimal.ZERO.setScale(PERCENT_SCALE);
    }
    return BigDecimal.valueOf(part)
        .multiply(HUNDRED)
        .divide(BigDecimal.valueOf(whole), PERCENT_SCALE, RoundingMode.HALF_UP);
  }
}
