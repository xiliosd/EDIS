package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.InputType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Protege las {@code ocultas} calculadas de los casos sembrados de 2026: cada caso nombra las
 * columnas que espera ocultas, y estas pruebas las confrontan con las reglas del archivo.
 */
class SeededCasesTest {

  private static final Path SEED =
      Path.of("src", "main", "resources", "rules-seed", "EDIN-2026.json");
  private static final List<String> OPTIONS = List.of("1", "2", "6", "9");

  @Test
  void allSeededCasesPassAgainstTheSeededRules() throws IOException {
    RuleSet seeded = JsonMapper.builder().build().readValue(Files.readString(SEED), RuleSet.class);
    Map<String, QuestionFacts> facts = activeFactsFor(seeded);

    List<CaseResult> results = CaseRunner.runAll(seeded, facts);

    assertThat(seeded.cases()).hasSize(12);
    for (CaseResult result : results) {
      assertThat(result.status()).as(result.id()).isEqualTo(CaseResult.Status.PASA);
      assertThat(result.differences()).as(result.id()).isEmpty();
    }
  }

  /** Todas las columnas que nombran los casos, activas, en la página de su caso. */
  private static Map<String, QuestionFacts> activeFactsFor(RuleSet seeded) {
    Map<String, String> pageOfColumn = new LinkedHashMap<>();
    for (TestCase testCase : seeded.cases()) {
      TreeSet<String> columns = new TreeSet<>(testCase.answers().keySet());
      columns.addAll(testCase.expected().hidden());
      columns.forEach(column -> pageOfColumn.putIfAbsent(column, testCase.pageCode()));
    }
    Map<String, QuestionFacts> facts = new LinkedHashMap<>();
    int position = 0;
    for (Map.Entry<String, String> entry : pageOfColumn.entrySet()) {
      facts.put(
          entry.getKey(),
          new QuestionFacts(
              entry.getKey(),
              entry.getValue(),
              position++,
              InputType.SINGLE_CHOICE,
              OPTIONS,
              ColumnStatus.ACTIVE,
              null));
    }
    return facts;
  }
}
