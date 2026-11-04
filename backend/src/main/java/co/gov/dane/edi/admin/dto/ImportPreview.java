package co.gov.dane.edi.admin.dto;

import java.util.List;

/**
 * Resultado de revisar (o confirmar) la importación de un paquete: su versión y tipo ({@code anio}
 * o {@code correccion}), el impacto por página frente al archivo vigente de este ambiente y si el
 * paquete ya estaba importado.
 */
public record ImportPreview(
    int version, String kind, List<PageImpact> impact, boolean alreadyImported) {

  public ImportPreview {
    impact = impact == null ? List.of() : List.copyOf(impact);
  }
}
