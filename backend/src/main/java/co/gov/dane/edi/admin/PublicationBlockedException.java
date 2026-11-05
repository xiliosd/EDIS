package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.dto.PublicationChecklist;

/**
 * El año no cumple la lista de publicación (HTTP 422): al publicarlo o al generar su paquete de
 * promoción.
 */
public class PublicationBlockedException extends RuntimeException {

  private final transient PublicationChecklist checklist;

  public PublicationBlockedException(PublicationChecklist checklist) {
    this("El cuestionario no cumple las condiciones para publicarse.", checklist);
  }

  public PublicationBlockedException(String message, PublicationChecklist checklist) {
    super(message);
    this.checklist = checklist;
  }

  public PublicationChecklist checklist() {
    return checklist;
  }
}
