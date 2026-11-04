package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.dto.PublicationChecklist;

/** El año no cumple las condiciones para publicarse (HTTP 422). */
public class PublicationBlockedException extends RuntimeException {

  private final transient PublicationChecklist checklist;

  public PublicationBlockedException(PublicationChecklist checklist) {
    super("El cuestionario no cumple las condiciones para publicarse.");
    this.checklist = checklist;
  }

  public PublicationChecklist checklist() {
    return checklist;
  }
}
