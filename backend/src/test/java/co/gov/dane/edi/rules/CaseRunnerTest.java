package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.InputType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CaseRunnerTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final RuleSet RULES =
      new RuleSet(
          "EDIN",
          2026,
          1,
          List.of(
              new SkipRule(
                  "c01a-no",
                  null,
                  new Condition("C01A", "eq", "2", null, null, null, null, null),
                  new SkipAction("deshabilitar", List.of("C01B")))),
          Map.of(),
          null,
          Map.of());

  private static Map<String, QuestionFacts> facts(ColumnStatus statusOfC02) {
    Map<String, QuestionFacts> facts = new LinkedHashMap<>();
    int position = 0;
    for (String column : List.of("C01A", "C01B", "C02")) {
      ColumnStatus status = column.equals("C02") ? statusOfC02 : ColumnStatus.ACTIVE;
      facts.put(
          column,
          new QuestionFacts(
              column, "C1", position++, InputType.SINGLE_CHOICE, List.of("1", "2"), status, null));
    }
    return facts;
  }

  private static Map<String, QuestionFacts> activeFacts() {
    return facts(ColumnStatus.ACTIVE);
  }

  private static TestCase testCase(
      String page, Map<String, String> answers, ExpectedOutcome expected, Boolean needsReview) {
    return new TestCase("caso-1", "Caso uno", page, answers, expected, needsReview);
  }

  @Test
  void passesWhenHiddenAndErrorsMatch() {
    TestCase testCase =
        testCase(
            "C1",
            Map.of("C01A", "2"),
            new ExpectedOutcome(List.of("C01B"), Map.of("C02", "REQUERIDA")),
            null);

    CaseResult result = CaseRunner.run(RULES, testCase, activeFacts());

    assertThat(result.status()).isEqualTo(CaseResult.Status.PASA);
    assertThat(result.actualHidden()).containsExactly("C01B");
    assertThat(result.actualErrors()).containsEntry("C02", "REQUERIDA");
    assertThat(result.differences()).isEmpty();
  }

  @Test
  void normalizesThePageCodeLikeSurveyPage() {
    TestCase testCase =
        testCase(" c1 ", Map.of("C01A", "2"), new ExpectedOutcome(List.of("C01B"), null), null);

    assertThat(CaseRunner.run(RULES, testCase, activeFacts()).status())
        .isEqualTo(CaseResult.Status.PASA);
  }

  @Test
  void nullExpectedErrorsAreNotCompared() {
    TestCase testCase =
        testCase("C1", Map.of("C01A", "2"), new ExpectedOutcome(List.of("C01B"), null), null);

    CaseResult result = CaseRunner.run(RULES, testCase, activeFacts());

    assertThat(result.status()).isEqualTo(CaseResult.Status.PASA);
    assertThat(result.actualErrors()).isNotEmpty();
  }

  @Test
  void failsListingEachDifference() {
    TestCase testCase =
        testCase(
            "C1",
            Map.of("C01A", "1"),
            new ExpectedOutcome(List.of("C01B"), Map.of("C02", "FUERA_DE_RANGO")),
            null);

    CaseResult result = CaseRunner.run(RULES, testCase, activeFacts());

    assertThat(result.status()).isEqualTo(CaseResult.Status.FALLA);
    assertThat(result.actualHidden()).isEmpty();
    assertThat(result.differences())
        .contains(
            "Se esperaba oculta C01B y está visible.",
            "Se esperaba el error FUERA_DE_RANGO en C02 y el error fue REQUERIDA.");
  }

  @Test
  void reportsUnexpectedHiddenAndMissingErrors() {
    TestCase testCase =
        testCase(
            "C1",
            Map.of("C01A", "2", "C02", "1"),
            new ExpectedOutcome(List.of(), Map.of("C01B", "REQUERIDA")),
            null);

    CaseResult result = CaseRunner.run(RULES, testCase, activeFacts());

    assertThat(result.status()).isEqualTo(CaseResult.Status.FALLA);
    assertThat(result.differences())
        .contains(
            "Se esperaba visible C01B y está oculta.",
            "Se esperaba el error REQUERIDA en C01B y no hubo error.");
  }

  @Test
  void caseReferencingInactiveQuestionNeedsReview() {
    TestCase testCase =
        testCase("C1", Map.of("C01A", "2"), new ExpectedOutcome(List.of("C02"), null), null);

    CaseResult result = CaseRunner.run(RULES, testCase, facts(ColumnStatus.PENDING_COLUMN));

    assertThat(result.status()).isEqualTo(CaseResult.Status.POR_REVISAR);
    assertThat(result.actualHidden()).isEmpty();
  }

  @Test
  void caseReferencingMissingQuestionNeedsReview() {
    TestCase testCase =
        testCase("C1", Map.of("Z99", "2"), new ExpectedOutcome(List.of(), null), null);

    assertThat(CaseRunner.run(RULES, testCase, activeFacts()).status())
        .isEqualTo(CaseResult.Status.POR_REVISAR);
  }

  @Test
  void flaggedCaseIsNotEvaluated() {
    TestCase testCase =
        testCase("C1", Map.of("C01A", "2"), new ExpectedOutcome(List.of("C01B"), null), true);

    CaseResult result = CaseRunner.run(RULES, testCase, activeFacts());

    assertThat(result.status()).isEqualTo(CaseResult.Status.POR_REVISAR);
    assertThat(result.actualHidden()).isEmpty();
    assertThat(result.actualErrors()).isEmpty();
  }

  @Test
  void runAllKeepsFileOrder() {
    ExpectedOutcome hidesC01b = new ExpectedOutcome(List.of("C01B"), null);
    TestCase first = new TestCase("a", "A", "C1", Map.of("C01A", "2"), hidesC01b, null);
    TestCase second = new TestCase("b", "B", "C1", Map.of("C01A", "1"), hidesC01b, null);
    RuleSet withCases = RULES.withCases(List.of(first, second));

    List<CaseResult> results = CaseRunner.runAll(withCases, activeFacts());

    assertThat(results).extracting(CaseResult::id).containsExactly("a", "b");
    assertThat(results)
        .extracting(CaseResult::status)
        .containsExactly(CaseResult.Status.PASA, CaseResult.Status.FALLA);
  }

  @Test
  void statusSerializesLowercase() {
    assertThat(JSON.writeValueAsString(CaseResult.Status.POR_REVISAR)).isEqualTo("\"por_revisar\"");
  }

  @Test
  void sharedSimulatorFixturePasses() throws IOException {
    JsonNode node =
        JSON.readTree(Path.of("..", "shared", "rules-fixtures", "07-caso-simulador.json").toFile());
    RuleSet rules = JSON.treeToValue(node.get("reglas"), RuleSet.class);
    TestCase testCase = JSON.treeToValue(node.get("caso"), TestCase.class);
    Map<String, QuestionFacts> facts = new LinkedHashMap<>();
    int position = 0;
    for (JsonNode column : node.get("columnas")) {
      facts.put(
          column.asString(),
          new QuestionFacts(
              column.asString(),
              testCase.pageCode(),
              position++,
              InputType.SINGLE_CHOICE,
              List.of("1", "2"),
              ColumnStatus.ACTIVE,
              null));
    }

    CaseResult result = CaseRunner.run(rules, testCase, facts);

    assertThat(result.differences()).isEmpty();
    assertThat(result.status()).isEqualTo(CaseResult.Status.PASA);
  }
}
