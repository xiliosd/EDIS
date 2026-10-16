package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Los mismos casos se ejecutan en el motor TypeScript (Tarea 12): ambos deben coincidir. */
class RuleEvaluatorFixturesTest {

  private static final Path FIXTURES = Path.of("..", "shared", "rules-fixtures");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  static Stream<Path> fixtures() throws IOException {
    return Files.list(FIXTURES).filter(p -> p.toString().endsWith(".json")).sorted();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void matchesTheSharedFixture(Path fixture) throws IOException {
    JsonNode node = JSON.readTree(fixture.toFile());
    RuleSet rules = JSON.treeToValue(node.get("reglas"), RuleSet.class);
    List<String> columns =
        JSON.convertValue(
            node.get("columnas"),
            JSON.getTypeFactory().constructCollectionType(List.class, String.class));
    Map<String, String> answers =
        JSON.convertValue(
            node.get("respuestas"),
            JSON.getTypeFactory().constructMapType(Map.class, String.class, String.class));
    List<String> expectedDisabled =
        JSON.convertValue(
            node.get("deshabilitadas"),
            JSON.getTypeFactory().constructCollectionType(List.class, String.class));
    Map<String, String> expectedErrors =
        JSON.convertValue(
            node.get("errores"),
            JSON.getTypeFactory().constructMapType(Map.class, String.class, String.class));

    Evaluation evaluation = RuleEvaluator.evaluate(rules, answers, columns);

    assertThat(evaluation.disabled())
        .containsExactlyElementsOf(expectedDisabled.stream().sorted().toList());
    assertThat(evaluation.errors()).isEqualTo(expectedErrors);
  }
}
