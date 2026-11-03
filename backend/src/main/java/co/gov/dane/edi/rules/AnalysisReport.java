package co.gov.dane.edi.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resultado del análisis: los errores bloquean el guardado, las advertencias nunca. */
public record AnalysisReport(List<Finding> errors, List<Finding> warnings) {

  public AnalysisReport {
    errors = errors == null ? List.of() : List.copyOf(errors);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  /** Los errores que el informe anterior no tenía, comparando código, elemento y preguntas. */
  public List<Finding> newErrorsComparedTo(AnalysisReport previous) {
    Set<Finding> known = new HashSet<>();
    previous.errors().forEach(finding -> known.add(finding.identity()));
    return errors.stream().filter(finding -> !known.contains(finding.identity())).toList();
  }
}
