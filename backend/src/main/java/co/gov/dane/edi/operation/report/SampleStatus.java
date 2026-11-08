package co.gov.dane.edi.operation.report;

/**
 * Estado de un funcionario de la muestra (criterio único de todos los reportes, diseño §5.1). Lo
 * completo prevalece sobre la novedad.
 */
public enum SampleStatus {
  COMPLETE,
  NOVELTY,
  INCOMPLETE,
  NOT_STARTED;

  /**
   * Expresión SQL del estado con los alias {@code b} (capítulo B), {@code h} (capítulo H) y {@code
   * m} (muestra); devuelve el nombre de la constante. Es la única definición del criterio.
   */
  public static final String STATUS_CASE =
      "CASE WHEN b.B07 IS NOT NULL AND h.H06 IS NOT NULL THEN 'COMPLETE'"
          + " WHEN m.IDFNOV IS NOT NULL THEN 'NOVELTY'"
          + " WHEN b.B07 IS NOT NULL THEN 'INCOMPLETE'"
          + " ELSE 'NOT_STARTED' END";
}
