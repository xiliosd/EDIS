package co.gov.dane.edi.operation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Zona horaria única de lo que ve el equipo operativo (Ruling 6): nombres de archivo, id y fecha de
 * los cortes van en hora de Bogotá, no en UTC; si no, después de las 19:00 un archivo llevaría la
 * fecha del día siguiente. El reloj de la aplicación sigue en UTC.
 */
public final class OperationalTime {

  public static final ZoneId ZONE = ZoneId.of("America/Bogota");

  private OperationalTime() {}

  /** Fecha de hoy en Bogotá. */
  public static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(ZONE));
  }

  /** Fecha y hora actuales con el desplazamiento de Bogotá. */
  public static OffsetDateTime now(Clock clock) {
    return OffsetDateTime.now(clock.withZone(ZONE));
  }

  /** El mismo instante expresado en hora de Bogotá. */
  public static OffsetDateTime inZone(OffsetDateTime dateTime) {
    return dateTime.atZoneSameInstant(ZONE).toOffsetDateTime();
  }
}
