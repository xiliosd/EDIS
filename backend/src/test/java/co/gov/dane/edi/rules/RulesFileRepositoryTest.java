package co.gov.dane.edi.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.config.RulesSeedProperties;
import co.gov.dane.edi.config.SurveyProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        new RulesSeedProperties(seedDir));
  }

  @Test
  void productionSeedFromTheClasspathIsUsedWithoutSeedDirectory() {
    RuleSet seeded = repository(tempDir, "EDIN", "").load();
    assertThat(seeded.survey()).isEqualTo("EDIN");
    // La semilla de producción nunca trae las validaciones del cuestionario ficticio.
    assertThat(seeded.validations()).doesNotContainKey("B06");
  }

  @Test
  void missingFileIsSeededAndCorruptFileFailsClearly() throws Exception {
    Path rulesDir = tempDir.resolve("no-existe-aun");
    RuleSet seeded = repository(rulesDir, "EDIN").load();

    assertThat(Files.exists(rulesDir.resolve("EDIN-2026.json"))).isTrue();
    assertThat(seeded.survey()).isEqualTo("EDIN");
    assertThat(seeded.validations()).containsKey("B06");

    Files.writeString(rulesDir.resolve("EDIN-2026.json"), "{no es json", StandardCharsets.UTF_8);
    assertThatThrownBy(() -> repository(rulesDir, "EDIN").load())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Archivo de reglas inválido: EDIN-2026.json");
  }

  @Test
  void surveyWithoutSeedGetsAnEmptyRuleSet() {
    RuleSet rules = repository(tempDir, "ZZZZ").load();
    assertThat(rules.skips()).isEmpty();
  }

  @Test
  void existingFileTakesPrecedenceOverSeed() throws Exception {
    Files.writeString(
        tempDir.resolve("EDIN-2026.json"),
        "{\"encuesta\":\"EDIN\",\"anio\":2026,\"version\":7,\"saltos\":[],\"validaciones\":{}}",
        StandardCharsets.UTF_8);
    assertThat(repository(tempDir, "EDIN").load().version()).isEqualTo(7);
  }

  @Test
  void loadIsCachedAfterTheFirstRead() {
    RulesFileRepository repository = repository(tempDir, "EDIN");
    assertThat(repository.load()).isSameAs(repository.load());
  }

  @Test
  void newerSeedThanTheExistingFileIsLoggedAsWarning(CapturedOutput output) throws Exception {
    Files.writeString(
        tempDir.resolve("EDIN-2026.json"),
        "{\"encuesta\":\"EDIN\",\"anio\":2026,\"version\":1,\"saltos\":[],\"validaciones\":{}}",
        StandardCharsets.UTF_8);

    // La semilla de producción (classpath) es la versión 2; el archivo vigente sigue mandando.
    assertThat(repository(tempDir, "EDIN", "").load().version()).isEqualTo(1);
    assertThat(output)
        .contains("La semilla de EDIN-2026.json tiene la versión 2 y el archivo vigente la 1");
  }

  @Test
  void olderOrEqualSeedIsNotLogged(CapturedOutput output) throws Exception {
    Files.writeString(
        tempDir.resolve("EDIN-2026.json"),
        "{\"encuesta\":\"EDIN\",\"anio\":2026,\"version\":7,\"saltos\":[],\"validaciones\":{}}",
        StandardCharsets.UTF_8);

    repository(tempDir, "EDIN", "").load();
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
}
