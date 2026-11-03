package co.gov.dane.edi.service.error;

import java.util.List;

/**
 * La pregunta no se puede quitar porque reglas o casos de prueba vigentes la mencionan (HTTP 409).
 */
public class ReferencedByRulesException extends RuntimeException {

  private final transient List<String> ruleIds;
  private final transient List<String> caseIds;

  public ReferencedByRulesException(List<String> ruleIds, List<String> caseIds) {
    super("Hay reglas o casos de prueba que usan esta pregunta. Quítela primero de ellos.");
    this.ruleIds = List.copyOf(ruleIds);
    this.caseIds = List.copyOf(caseIds);
  }

  public List<String> ruleIds() {
    return ruleIds;
  }

  public List<String> caseIds() {
    return caseIds;
  }
}
