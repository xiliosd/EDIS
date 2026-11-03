package co.gov.dane.edi.rules;

import co.gov.dane.edi.model.ColumnStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ejecuta los casos de prueba de un año contra las reglas efectivas. Un caso marcado por revisar, o
 * que menciona una pregunta que ya no está activa, no se evalúa: sus respuestas ya no describen el
 * cuestionario y darlo por pasado ocultaría un cambio que el equipo debe revisar.
 */
public final class CaseRunner {

  private CaseRunner() {}

  public static List<CaseResult> runAll(RuleSet effective, Map<String, QuestionFacts> facts) {
    return effective.cases().stream().map(testCase -> run(effective, testCase, facts)).toList();
  }

  public static CaseResult run(
      RuleSet effective, TestCase testCase, Map<String, QuestionFacts> facts) {
    if (testCase.isNeedsReview() || referencesInactiveQuestion(testCase, facts)) {
      return new CaseResult(
          testCase.id(), CaseResult.Status.POR_REVISAR, List.of(), Map.of(), List.of());
    }
    String page = testCase.pageCode() == null ? "" : testCase.pageCode().strip();
    String normalizedPage = page.toUpperCase(Locale.ROOT);
    List<String> pageItems =
        facts.values().stream()
            .filter(question -> isActive(question) && question.pageCode().equals(normalizedPage))
            .sorted(Comparator.comparingInt(QuestionFacts::position))
            .map(QuestionFacts::column)
            .toList();
    Evaluation evaluation = RuleEvaluator.evaluate(effective, testCase.answers(), pageItems);
    List<String> actualHidden =
        evaluation.disabled().stream()
            .filter(column -> isActive(facts.get(column)))
            .sorted()
            .toList();
    Map<String, String> actualErrors = Map.copyOf(evaluation.errors());
    List<String> differences = compare(testCase.expected(), actualHidden, actualErrors);
    CaseResult.Status status =
        differences.isEmpty() ? CaseResult.Status.PASA : CaseResult.Status.FALLA;
    return new CaseResult(testCase.id(), status, actualHidden, actualErrors, differences);
  }

  private static List<String> compare(
      ExpectedOutcome expected, List<String> actualHidden, Map<String, String> actualErrors) {
    List<String> differences = new ArrayList<>();
    for (String column : expected.hidden()) {
      if (!actualHidden.contains(column)) {
        differences.add("Se esperaba oculta " + column + " y está visible.");
      }
    }
    for (String column : actualHidden) {
      if (!expected.hidden().contains(column)) {
        differences.add("Se esperaba visible " + column + " y está oculta.");
      }
    }
    if (expected.errors() != null) {
      Set<String> columns = new TreeSet<>(expected.errors().keySet());
      columns.addAll(actualErrors.keySet());
      for (String column : columns) {
        String wanted = expected.errors().get(column);
        String actual = actualErrors.get(column);
        if (wanted != null && actual == null) {
          differences.add("Se esperaba el error " + wanted + " en " + column + " y no hubo error.");
        } else if (wanted != null && !wanted.equals(actual)) {
          differences.add(
              "Se esperaba el error "
                  + wanted
                  + " en "
                  + column
                  + " y el error fue "
                  + actual
                  + ".");
        } else if (wanted == null) {
          differences.add("No se esperaba error en " + column + " y hubo el error " + actual + ".");
        }
      }
    }
    return differences;
  }

  private static boolean referencesInactiveQuestion(
      TestCase testCase, Map<String, QuestionFacts> facts) {
    Set<String> referenced = new TreeSet<>(testCase.answers().keySet());
    referenced.addAll(testCase.expected().hidden());
    if (testCase.expected().errors() != null) {
      referenced.addAll(testCase.expected().errors().keySet());
    }
    return referenced.stream().anyMatch(column -> !isActive(facts.get(column)));
  }

  private static boolean isActive(QuestionFacts question) {
    return question != null && question.columnStatus() == ColumnStatus.ACTIVE;
  }
}
