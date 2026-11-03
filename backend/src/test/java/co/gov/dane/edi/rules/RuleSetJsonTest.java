package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RuleSetJsonTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  private static TestCase simulatorCase(Boolean needsReview) {
    return new TestCase(
        "c01a-no",
        "C01A en No oculta C01B",
        "C1",
        Map.of("C01A", "2"),
        new ExpectedOutcome(List.of("C01B"), Map.of()),
        needsReview);
  }

  @Test
  void casesRoundTripAndAreOmittedWhenEmpty() throws Exception {
    RuleSet withCase = RuleSet.empty("EDIN", 2026).withCases(List.of(simulatorCase(null)));

    String json = mapper.writeValueAsString(withCase);

    assertThat(json)
        .contains(
            "\"casos\":[{\"id\":\"c01a-no\",\"nombre\":\"C01A en No oculta C01B\","
                + "\"pagina\":\"C1\",\"respuestas\":{\"C01A\":\"2\"},"
                + "\"esperado\":{\"ocultas\":[\"C01B\"],\"errores\":{}}}]")
        .doesNotContain("porRevisar");
    assertThat(mapper.readValue(json, RuleSet.class)).isEqualTo(withCase);
    assertThat(mapper.writeValueAsString(RuleSet.empty("EDIN", 2026))).doesNotContain("casos");
  }

  @Test
  void reviewFlagIsWrittenOnlyWhenTrue() throws Exception {
    assertThat(mapper.writeValueAsString(simulatorCase(false))).doesNotContain("porRevisar");
    String json = mapper.writeValueAsString(simulatorCase(true));
    assertThat(json).contains("\"porRevisar\":true");
    assertThat(mapper.readValue(json, TestCase.class).isNeedsReview()).isTrue();
  }

  @Test
  void expectedErrorsAreOmittedWhenNotCompared() throws Exception {
    String json = mapper.writeValueAsString(new ExpectedOutcome(List.of("B", "A"), null));

    assertThat(json).isEqualTo("{\"ocultas\":[\"A\",\"B\"]}");
  }
}
