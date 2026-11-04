package co.gov.dane.edi.admin;

/**
 * Clase de un cambio al cuestionario, para saber qué admite un año publicado. {@code STRUCTURAL}:
 * agregar, desactivar, reordenar, mapear tablas o cambiar columnas; solo en borrador. {@code
 * CORRECTION}: textos, encabezados, ayudas, saltos, validaciones y casos; también en un año
 * publicado, con motivo.
 */
public enum EditKind {
  STRUCTURAL,
  CORRECTION;

  private static final String CORRECTION_PREFIX = "corregir-";

  /**
   * Operación auditada de una corrección: {@code corregir-} más la de siempre. Sigue siendo una
   * clave fija de auditoría (minúsculas, números y guiones, hasta 41 caracteres).
   */
  public static String correctionOperation(String operation) {
    return CORRECTION_PREFIX + operation;
  }
}
