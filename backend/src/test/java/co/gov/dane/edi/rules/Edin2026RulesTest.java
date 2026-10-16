package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.model.AnswerOption;
import co.gov.dane.edi.model.AnswerSpec;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.EntryKind;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.model.LegacySkip;
import co.gov.dane.edi.model.QuestionnaireEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las reglas efectivas de EDIN 2026: la semilla de producción más los saltos genéricos de
 * FRM_SALTO, armados con el orden real de db/reference/frm-2026-orden.csv.
 */
class Edin2026RulesTest {

  private static final Path REFERENCE = Path.of("..", "db", "reference", "frm-2026-orden.csv");
  private static final Map<String, List<String>> COLUMNS_BY_PAGE = new LinkedHashMap<>();
  private static RuleSet rules;

  @BeforeAll
  static void loadRules() throws IOException {
    Map<String, List<QuestionnaireEntry>> entriesByPage = new LinkedHashMap<>();
    List<String> lines = Files.readAllLines(REFERENCE, StandardCharsets.UTF_8);
    long nextId = 1;
    for (String line : lines.subList(1, lines.size())) {
      String[] field = line.split(",", -1);
      String page = field[0];
      String column = field[3];
      LegacySkip skip =
          field[6].isEmpty() ? null : new LegacySkip(Integer.parseInt(field[6]), field[7]);
      entriesByPage
          .computeIfAbsent(page, key -> new ArrayList<>())
          .add(
              new QuestionnaireEntry(
                  nextId++,
                  page,
                  EntryKind.ITEM,
                  field[2],
                  field[2],
                  "",
                  List.of(),
                  new AnswerSpec(
                      InputType.SINGLE_CHOICE,
                      column,
                      ColumnStatus.ACTIVE,
                      List.<AnswerOption>of()),
                  skip));
      COLUMNS_BY_PAGE.computeIfAbsent(page, key -> new ArrayList<>()).add(column);
    }
    List<SkipRule> legacy = new ArrayList<>();
    entriesByPage
        .values()
        .forEach(entries -> legacy.addAll(LegacySkipTranslator.translate(entries)));
    try (InputStream seed =
        Edin2026RulesTest.class.getResourceAsStream("/rules-seed/EDIN-2026.json")) {
      RuleSet fromFile = JsonMapper.builder().build().readValue(seed, RuleSet.class);
      RuleSetValidator.validate(fromFile);
      rules = EffectiveRulesService.merge(fromFile, legacy);
    }
  }

  private static Set<String> disabled(String page, Map<String, String> answers) {
    return RuleEvaluator.evaluate(rules, answers, COLUMNS_BY_PAGE.get(page)).disabled();
  }

  private static Map<String, String> all(String value, String... columns) {
    Map<String, String> answers = new HashMap<>();
    Stream.of(columns).forEach(column -> answers.put(column, value));
    return answers;
  }

  @Test
  void everyColumnInTheRulesExistsInThe2026Questionnaire() {
    Set<String> known = new HashSet<>();
    COLUMNS_BY_PAGE.values().forEach(known::addAll);
    for (SkipRule rule : rules.skips()) {
      assertThat(known).as("destinos de %s", rule.id()).containsAll(rule.then().questions());
    }
    assertThat(rules.validations()).isEmpty();
  }

  @Test
  void c07aNoSkipsC08() {
    assertThat(disabled("C1", Map.of("C07A", "2")))
        .contains("C08A", "C08B", "C08C", "C08D", "C08E", "C08F", "C08G", "C08H");
    assertThat(disabled("C1", Map.of("C07A", "1"))).doesNotContain("C08A");
  }

  @Test
  void c10OnlySkipsWhenAllC09AreNo() {
    String[] c09 = {"C09A", "C09B", "C09C", "C09D", "C09E", "C09F", "C09G"};
    assertThat(disabled("C1", all("2", c09))).contains("C10");
    Map<String, String> onlyLastIsNo = all("1", c09);
    onlyLastIsNo.put("C09G", "2");
    assertThat(disabled("C1", onlyLastIsNo)).doesNotContain("C10");
  }

  @Test
  void teleworkSkipsC14ToC16() {
    assertThat(disabled("C2", Map.of("C13", "6")))
        .contains("C14", "C15", "C16A", "C16B", "C16C", "C16D", "C16E", "C16F");
    assertThat(disabled("C2", Map.of("C13", "1"))).doesNotContain("C14");
  }

  @Test
  void d09ChoosesBetweenD10AndD11() {
    assertThat(disabled("D2", Map.of("D09", "1"))).contains("D11").doesNotContain("D10");
    assertThat(disabled("D2", Map.of("D09", "4"))).contains("D10").doesNotContain("D11");
    assertThat(disabled("D2", Map.of("D09", "9"))).contains("D10", "D11");
  }

  @Test
  void e01AllNoSkipsE02() {
    assertThat(disabled("E1", all("2", "E01A", "E01B", "E01C", "E01D", "E01E", "E01F")))
        .contains("E02");
    assertThat(disabled("E1", all("2", "E01A", "E01B"))).doesNotContain("E02");
  }

  @Test
  void f03AndF04Rules() {
    assertThat(disabled("F1", Map.of("F03A", "2")))
        .contains("F04A", "F04B", "F04C", "F04D", "F04E", "F04F", "F04G", "F04H", "F04I");
    String[] f04 = {"F04A", "F04B", "F04C", "F04D", "F04E", "F04F", "F04G", "F04H"};
    Map<String, String> allNo = all("2", f04);
    allNo.put("F03A", "1");
    assertThat(disabled("F1", allNo)).contains("F04I");
    assertThat(disabled("F1", Map.of("F03A", "1", "F04C", "9"))).contains("F04I");
    assertThat(disabled("F1", Map.of("F03A", "1", "F04C", "2"))).doesNotContain("F04I");
    assertThat(disabled("F1", Map.of("F03J", "2"))).contains("F05A", "F05B", "F05C", "F05D");
    assertThat(disabled("F1", Map.of("F03J", "1"))).doesNotContain("F05A");
  }

  @Test
  void g05GreaterThanOneSkipsG06() {
    assertThat(disabled("G1", Map.of("G05", "2"))).contains("G06");
    assertThat(disabled("G1", Map.of("G05", "1"))).doesNotContain("G06");
  }

  @Test
  void genericLegacySkipsComeFromFrmSalto() {
    assertThat(disabled("B1", Map.of("B10", "2"))).contains("B11");
    assertThat(disabled("C2", Map.of("C19", "2"))).contains("C20A", "C20B");
    assertThat(disabled("D1", Map.of("D04", "2")))
        .contains("D05", "D06A", "D06B", "D06C", "D06D", "D06E", "D07");
    assertThat(disabled("E1", Map.of("E05", "2"))).contains("E06A", "E06B", "E06C", "E06D");
    assertThat(disabled("E1", Map.of("E07", "2"))).contains("E08A", "E08B", "E08C", "E08D");
    assertThat(disabled("G1", Map.of("G02", "2"))).contains("G03A", "G03B", "G03C", "G03D", "G03E");
  }
}
