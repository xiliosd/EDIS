package co.gov.dane.edi.operation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OperationalTimeTest {

  /** 2026-11-08 02:30 UTC es aún el 7 de noviembre a las 21:30 en Bogotá. */
  private static final Clock AFTER_SEVEN_PM_IN_BOGOTA =
      Clock.fixed(Instant.parse("2026-11-08T02:30:00Z"), ZoneOffset.UTC);

  @Test
  void todayUsesBogotaDateNotUtc() {
    assertThat(OperationalTime.today(AFTER_SEVEN_PM_IN_BOGOTA))
        .isEqualTo(LocalDate.of(2026, 11, 7));
  }

  @Test
  void nowCarriesBogotaOffset() {
    OffsetDateTime now = OperationalTime.now(AFTER_SEVEN_PM_IN_BOGOTA);

    assertThat(now.getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    assertThat(now.getHour()).isEqualTo(21);
    assertThat(now.toInstant()).isEqualTo(AFTER_SEVEN_PM_IN_BOGOTA.instant());
  }

  @Test
  void inZoneKeepsTheInstant() {
    OffsetDateTime utc = OffsetDateTime.of(2026, 11, 8, 2, 30, 0, 0, ZoneOffset.UTC);

    OffsetDateTime bogota = OperationalTime.inZone(utc);

    assertThat(bogota.getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    assertThat(bogota.isEqual(utc)).isTrue();
  }
}
