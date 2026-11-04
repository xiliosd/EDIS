package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.admin.YearState;
import java.time.Instant;
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

  @Test
  void withersKeepCases() {
    List<TestCase> cases = List.of(simulatorCase(null));
    RuleSet base = RuleSet.empty("EDIN", 2026).withCases(cases);

    assertThat(base.withState(YearState.BORRADOR).cases()).isEqualTo(cases);
    assertThat(base.withTables(Map.of("B", "TABLA_B")).cases()).isEqualTo(cases);
    assertThat(base.withVersion(5).cases()).isEqualTo(cases);
    assertThat(base.withNewColumns(Map.of()).cases()).isEqualTo(cases);
    assertThat(base.withSkips(List.of()).cases()).isEqualTo(cases);
    assertThat(base.withValidations(Map.of()).cases()).isEqualTo(cases);
  }

  @Test
  void publicationAndLastChangeRoundTripAndAreOmittedWhenNull() throws Exception {
    Publication publication =
        new Publication(
            Instant.parse("2026-11-04T10:00:00Z"),
            "admin1",
            4,
            List.of("NO_ANALIZABLE@regla:f04-alguna-si"));
    ChangeNote change =
        new ChangeNote("admin1", Instant.parse("2026-11-04T11:00:00Z"), "publicar", null);
    RuleSet rules = RuleSet.empty("EDIN", 2026).withPublication(publication).withLastChange(change);

    String json = mapper.writeValueAsString(rules);

    assertThat(json)
        .contains(
            "\"publicacion\":{\"fecha\":\"2026-11-04T10:00:00Z\",\"usuario\":\"admin1\","
                + "\"version\":4,\"avisos\":[\"NO_ANALIZABLE@regla:f04-alguna-si\"]}")
        .contains(
            "\"ultimoCambio\":{\"usuario\":\"admin1\",\"fecha\":\"2026-11-04T11:00:00Z\","
                + "\"operacion\":\"publicar\"}")
        .doesNotContain("motivo");
    assertThat(mapper.readValue(json, RuleSet.class)).isEqualTo(rules);
    String empty = mapper.writeValueAsString(RuleSet.empty("EDIN", 2026));
    assertThat(empty).doesNotContain("publicacion").doesNotContain("ultimoCambio");
  }

  @Test
  void withersKeepPublicationAndLastChange() {
    Publication publication = new Publication(Instant.EPOCH, "admin1", 2, List.of());
    ChangeNote change = new ChangeNote("admin1", Instant.EPOCH, "x", "motivo de prueba");
    RuleSet base = RuleSet.empty("EDIN", 2026).withPublication(publication).withLastChange(change);

    for (RuleSet changed :
        List.of(
            base.withState(YearState.BORRADOR),
            base.withTables(Map.of("B", "TABLA_B")),
            base.withVersion(5),
            base.withNewColumns(Map.of()),
            base.withSkips(List.of()),
            base.withValidations(Map.of()),
            base.withCases(List.of()))) {
      assertThat(changed.publication()).isEqualTo(publication);
      assertThat(changed.lastChange()).isEqualTo(change);
    }
    assertThat(base.withPublication(null).lastChange()).isEqualTo(change);
    assertThat(base.withLastChange(null).publication()).isEqualTo(publication);
  }
}
