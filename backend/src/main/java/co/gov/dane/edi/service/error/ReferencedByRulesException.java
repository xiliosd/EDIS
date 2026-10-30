package co.gov.dane.edi.service.error;

import java.util.List;

/** La pregunta no se puede quitar porque reglas vigentes la mencionan (HTTP 409). */
public class ReferencedByRulesException extends RuntimeException {

  private final transient List<String> ruleIds;

  public ReferencedByRulesException(List<String> ruleIds) {
    super("Hay reglas que usan esta pregunta. Quítela primero de ellas.");
    this.ruleIds = List.copyOf(ruleIds);
  }

  public List<String> ruleIds() {
    return ruleIds;
  }
}
