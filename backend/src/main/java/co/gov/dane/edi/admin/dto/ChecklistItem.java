package co.gov.dane.edi.admin.dto;

import java.util.List;

/**
 * Una condición de la lista de comprobación de la publicación. {@code code}: SIN_ERRORES,
 * CASOS_EN_VERDE, COLUMNAS_CREADAS o TABLAS_VALIDAS. {@code elements}: ids de reglas, casos,
 * columnas o capítulos que impiden cumplirla (hasta 50), nunca texto de las preguntas.
 */
public record ChecklistItem(String code, boolean passed, String detail, List<String> elements) {

  public ChecklistItem {
    elements = elements == null ? List.of() : List.copyOf(elements);
  }
}
