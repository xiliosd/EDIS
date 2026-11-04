package co.gov.dane.edi.admin.dto;

import java.util.List;

/**
 * Condiciones para publicar un año. {@code warnings}: avisos del análisis como {@code
 * CODIGO@elemento}; nunca bloquean y quedan registrados al publicar.
 */
public record PublicationChecklist(
    int version, boolean ready, List<ChecklistItem> items, List<String> warnings) {

  public PublicationChecklist {
    items = items == null ? List.of() : List.copyOf(items);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
