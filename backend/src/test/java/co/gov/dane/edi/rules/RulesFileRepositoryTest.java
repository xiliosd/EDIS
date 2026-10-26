package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.admin.AdminAudit;
import co.gov.dane.edi.admin.YearState;
import co.gov.dane.edi.config.RulesSeedProperties;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.service.error.ConflictException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class RulesFileRepositoryTest {

  private static final Path DEV_SEED_DIR = Path.of("..", "db", "dev", "rules-seed");

  @TempDir Path tempDir;

  private RulesFileRepository repository(Path rulesDir, String code) {
    return repository(rulesDir, code, DEV_SEED_DIR.toString());
  }

  private RulesFileRepository repository(Path rulesDir, String code, String seedDir) {
    return new RulesFileRepository(
        JsonMapper.builder().build(),
        new SurveyProperties(code, 2026, "S", rulesDir.toString()),
        new RulesSeedProperties(seedDir),
        new AdminAudit());
  }

  @Test
  void productionSeedFromTheClasspathIsUsedWithoutSeedDirectory() {
    RuleSet seeded = repository(tempDir, "EDIN", "").load("EDIN", 2026);
    assertThat(seeded.survey()).isEqualTo("EDIN");
    // La semilla de producción nunca trae las validaciones del cuestionario ficticio.
    assertThat(seeded.validations()).doesNotContainKey("B06");
  }

  @Test
  void missingFileIsSeededAndCorruptFileFailsClearly() throws Exception {
    Path rulesDir = tempDir.resolve("no-existe-aun");
    RuleSet seeded = repository(rulesDir, "EDIN").load("EDIN", 2026);

    assertThat(Files.exists(rulesDir.resolve("EDIN-2026.json"))).isTrue();
    assertThat(seeded.survey()).isEqualTo("EDIN");
    assertThat(seeded.validations()).containsKey("B06");

    Files.writeString(rulesDir.resolve("EDIN-2026.json"), "{no es json", StandardCharsets.UTF_8);
    assertThatThrownBy(() -> repository(rulesDir, "EDIN").load("EDIN", 2026))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Archivo de reglas inválido: EDIN-2026.json");
  }

  @Test
  void surveyWithoutSeedGetsAnEmptyRuleSet() {
    RuleSet rules = repository(tempDir, "ZZZZ").load("ZZZZ", 2026);
    assertThat(rules.skips()).isEmpty();
  }

  @Test
  void existingFileTakesPrecedenceOverSeed() throws Exception {
    Files.writeString(
        tempDir.resolve("EDIN-2026.json"),
        "{\"encuesta\":\"EDIN\",\"anio\":2026,\"version\":7,\"saltos\":[],\"validaciones\":{}}",
        StandardCharsets.UTF_8);
    assertThat(repository(tempDir, "EDIN").load("EDIN", 2026).version()).isEqualTo(7);
  }

  @Test
  void loadIsCachedAfterTheFirstRead() {
    RulesFileRepository repository = repository(tempDir, "EDIN");
    assertThat(repository.load("EDIN", 2026)).isSameAs(repository.load("EDIN", 2026));
  }

  @Test
  void newerSeedThanTheExistingFileIsLoggedAsWarning(CapturedOutput output) throws Exception {
    Files.writeString(
        tempDir.resolve("EDIN-2026.json"),
        "{\"encuesta\":\"EDIN\",\"anio\":2026,\"version\":1,\"saltos\":[],\"validaciones\":{}}",
        StandardCharsets.UTF_8);

    // La semilla de producción (classpath) es la versión 2; el archivo vigente sigue mandando.
    assertThat(repository(tempDir, "EDIN", "").load("EDIN", 2026).version()).isEqualTo(1);
    assertThat(output)
        .contains("La semilla de EDIN-2026.json tiene la versión 2 y el archivo vigente la 1");
  }

  @Test
  void olderOrEqualSeedIsNotLogged(CapturedOutput output) throws Exception {
    Files.writeString(
        tempDir.resolve("EDIN-2026.json"),
        "{\"encuesta\":\"EDIN\",\"anio\":2026,\"version\":7,\"saltos\":[],\"validaciones\":{}}",
        StandardCharsets.UTF_8);

    repository(tempDir, "EDIN", "").load("EDIN", 2026);
    assertThat(output).doesNotContain("La semilla de EDIN-2026.json");
  }

  @Test
  void seedCopiedByAnotherRequestFirstIsKept() throws Exception {
    Path file = tempDir.resolve("EDIN-2026.json");
    Files.writeString(file, "ganó la otra petición", StandardCharsets.UTF_8);

    boolean copied =
        RulesFileRepository.copySeed(
            new ByteArrayInputStream("semilla".getBytes(StandardCharsets.UTF_8)), file);

    assertThat(copied).isFalse();
    assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("ganó la otra petición");
    try (var files = Files.list(tempDir)) {
      assertThat(files).containsExactly(file);
    }
  }

  private static void writeYear(Path dir, String code, int year, int version, String extra)
      throws Exception {
    Files.createDirectories(dir);
    Files.writeString(
        dir.resolve(code + "-" + year + ".json"),
        "{\"encuesta\":\""
            + code
            + "\",\"anio\":"
            + year
            + ",\"version\":"
            + version
            + extra
            + ",\"saltos\":[],\"validaciones\":{}}",
        StandardCharsets.UTF_8);
  }

  @Test
  void stateDefaultsToPublishedWhenMissing() {
    RuleSet seeded = repository(tempDir, "EDIN").load("EDIN", 2026);
    assertThat(seeded.state()).isNull();
    assertThat(seeded.effectiveState()).isEqualTo(YearState.PUBLICADO);
    assertThat(seeded.tables()).isEmpty();
  }

  @Test
  void stateAndTablesAreReadFromTheFile() throws Exception {
    writeYear(
        tempDir, "EDIN", 2027, 1, ",\"estado\":\"borrador\",\"tablas\":{\"B\":\"MI_TABLA_B\"}");
    RuleSet rules = repository(tempDir, "EDIN").load("EDIN", 2027);
    assertThat(rules.state()).isEqualTo(YearState.BORRADOR);
    assertThat(rules.tables()).containsEntry("B", "MI_TABLA_B");
  }

  @Test
  void saveWritesHistoryAndBumpsVersion() throws Exception {
    writeYear(tempDir, "EDIN", 2027, 3, ",\"estado\":\"borrador\"");
    RulesFileRepository repository = repository(tempDir, "EDIN");
    RuleSet current = repository.load("EDIN", 2027);

    RuleSet saved =
        repository.save(current.withTables(Map.of("B", "OTRA_TABLA")), 3, "admin1", "tablas");

    assertThat(saved.version()).isEqualTo(4);
    assertThat(repository.load("EDIN", 2027)).isSameAs(saved);
    JsonMapper mapper = JsonMapper.builder().build();
    RuleSet onDisk = mapper.readValue(tempDir.resolve("EDIN-2027.json").toFile(), RuleSet.class);
    assertThat(onDisk.version()).isEqualTo(4);
    assertThat(onDisk.tables()).containsEntry("B", "OTRA_TABLA");
    try (var history = Files.list(tempDir.resolve("historial"))) {
      List<Path> copies = history.toList();
      assertThat(copies).hasSize(1);
      assertThat(copies.get(0).getFileName().toString()).matches("EDIN-2027\\.\\d+\\.json");
      assertThat(mapper.readValue(copies.get(0).toFile(), RuleSet.class).version()).isEqualTo(3);
    }
    try (var files = Files.list(tempDir)) {
      assertThat(files.map(f -> f.getFileName().toString()))
          .containsExactlyInAnyOrder("EDIN-2027.json", "historial");
    }
  }

  @Test
  void saveWithStaleVersionConflicts() throws Exception {
    writeYear(tempDir, "EDIN", 2027, 3, "");
    RulesFileRepository repository = repository(tempDir, "EDIN");
    RuleSet current = repository.load("EDIN", 2027);

    assertThatThrownBy(() -> repository.save(current, 2, "admin1", "x"))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Otro usuario modificó el cuestionario. Recargue la página.");
    assertThat(repository.load("EDIN", 2027).version()).isEqualTo(3);
  }

  @Test
  void saveIsAtomic() throws Exception {
    writeYear(tempDir, "EDIN", 2027, 3, "");
    String before = Files.readString(tempDir.resolve("EDIN-2027.json"), StandardCharsets.UTF_8);
    RulesFileRepository failing =
        new RulesFileRepository(
            JsonMapper.builder().build(),
            new SurveyProperties("EDIN", 2026, "S", tempDir.toString()),
            new RulesSeedProperties(""),
            new AdminAudit()) {
          @Override
          void writeFile(Path file, RuleSet rules) throws IOException {
            Files.writeString(file, "{medio", StandardCharsets.UTF_8);
            throw new IOException("disco lleno");
          }
        };
    RuleSet current = failing.load("EDIN", 2027);

    assertThatThrownBy(() -> failing.save(current, 3, "admin1", "x"))
        .isInstanceOf(UncheckedIOException.class);

    assertThat(Files.readString(tempDir.resolve("EDIN-2027.json"), StandardCharsets.UTF_8))
        .isEqualTo(before);
    assertThat(failing.load("EDIN", 2027).version()).isEqualTo(3);
    try (var files = Files.list(tempDir)) {
      assertThat(files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".tmp")))
          .isEmpty();
    }
  }

  @Test
  void yearsListsExistingFiles() throws Exception {
    writeYear(tempDir, "EDIN", 2027, 1, "");
    writeYear(tempDir, "EDIN", 2026, 1, "");
    writeYear(tempDir, "EDID", 2025, 1, "");
    Files.writeString(tempDir.resolve("EDIN-borrador.txt"), "x", StandardCharsets.UTF_8);
    RulesFileRepository repository = repository(tempDir, "EDIN");

    assertThat(repository.years("EDIN")).containsExactly(2026, 2027);
    assertThat(repository.years("EDID")).containsExactly(2025);
    assertThat(repository(tempDir.resolve("vacio"), "EDIN").years("EDIN")).isEmpty();
  }

  @Test
  void findNeverSeeds() {
    RulesFileRepository repository = repository(tempDir, "EDIN");
    assertThat(repository.find("EDIN", 2026)).isEmpty();
    assertThat(Files.exists(tempDir.resolve("EDIN-2026.json"))).isFalse();
  }

  @Test
  void evictForcesAReadFromDisk() throws Exception {
    writeYear(tempDir, "EDIN", 2027, 1, "");
    RulesFileRepository repository = repository(tempDir, "EDIN");
    assertThat(repository.load("EDIN", 2027).version()).isEqualTo(1);
    writeYear(tempDir, "EDIN", 2027, 9, "");
    repository.evict();
    assertThat(repository.load("EDIN", 2027).version()).isEqualTo(9);
  }

  @Test
  void rejectsSurveyCodesThatCouldEscapeTheDirectory() {
    RulesFileRepository repository = repository(tempDir, "EDIN");
    assertThatThrownBy(() -> repository.load("../X", 2027))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
