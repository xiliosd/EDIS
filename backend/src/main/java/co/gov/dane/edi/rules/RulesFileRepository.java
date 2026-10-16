package co.gov.dane.edi.rules;

import co.gov.dane.edi.config.RulesSeedProperties;
import co.gov.dane.edi.config.SurveyProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Archivo de reglas en {@code RULES_DIR/<CODIGO>-<AÑO>.json} (spec §5.3). Si no existe se copia la
 * semilla (de {@code RULES_SEED_DIR} en desarrollo, del classpath en producción); una sola
 * instancia (D6) permite cachearlo en memoria.
 */
@Repository
public class RulesFileRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(RulesFileRepository.class);

  private final JsonMapper jsonMapper;
  private final SurveyProperties survey;
  private final RulesSeedProperties seed;
  private final AtomicReference<RuleSet> cached = new AtomicReference<>();

  public RulesFileRepository(
      JsonMapper jsonMapper, SurveyProperties survey, RulesSeedProperties seed) {
    this.jsonMapper = jsonMapper;
    this.survey = survey;
    this.seed = seed;
  }

  public RuleSet load() {
    RuleSet current = cached.get();
    if (current != null) {
      return current;
    }
    RuleSet loaded = readOrSeed();
    RuleSetValidator.validate(loaded);
    cached.compareAndSet(null, loaded);
    return cached.get();
  }

  private RuleSet readOrSeed() {
    String fileName = survey.code() + "-" + survey.year() + ".json";
    Path file = Path.of(survey.rulesDir()).resolve(fileName);
    try {
      boolean seeded = false;
      if (Files.notExists(file)) {
        Files.createDirectories(file.getParent());
        try (InputStream seedContent = openSeed(fileName)) {
          if (seedContent == null) {
            LOGGER.warn("Sin semilla de reglas para {}; se usan reglas vacías", fileName);
            return RuleSet.empty(survey.code(), survey.year());
          }
          seeded = copySeed(seedContent, file);
        }
      }
      RuleSet rules = jsonMapper.readValue(file.toFile(), RuleSet.class);
      if (!seeded) {
        warnIfSeedIsNewer(fileName, rules.version());
      }
      return rules;
    } catch (JacksonException exception) {
      // El contenido no se registra: puede ser grande y no aporta al diagnóstico.
      LOGGER.error("Archivo de reglas inválido: {}", fileName);
      throw new IllegalStateException("Archivo de reglas inválido: " + fileName, exception);
    } catch (IOException exception) {
      throw new UncheckedIOException("No fue posible leer las reglas " + fileName, exception);
    }
  }

  /**
   * Copia la semilla a un temporal y lo mueve sin reemplazar: otra petición que siembre a la vez
   * nunca deja leer un archivo a medio escribir, y si ganó la carrera se lee el suyo.
   */
  static boolean copySeed(InputStream seedContent, Path file) throws IOException {
    Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
    try {
      Files.copy(seedContent, temporary, StandardCopyOption.REPLACE_EXISTING);
      Files.move(temporary, file);
      LOGGER.info("Reglas sembradas en {}", file.getFileName());
      return true;
    } catch (FileAlreadyExistsException exception) {
      LOGGER.info("Otra petición sembró {} primero; se lee ese archivo", file.getFileName());
      return false;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  /** El archivo vigente manda (spec §5.3), pero una semilla más nueva merece revisión manual. */
  private void warnIfSeedIsNewer(String fileName, int fileVersion) {
    try (InputStream seedContent = openSeed(fileName)) {
      if (seedContent == null) {
        return;
      }
      int seedVersion = jsonMapper.readValue(seedContent, RuleSet.class).version();
      if (seedVersion > fileVersion) {
        LOGGER.warn(
            "La semilla de {} tiene la versión {} y el archivo vigente la {}",
            fileName,
            seedVersion,
            fileVersion);
      }
    } catch (JacksonException | IOException exception) {
      LOGGER.warn("No fue posible comparar la versión de la semilla de {}", fileName);
    }
  }

  private InputStream openSeed(String fileName) throws IOException {
    if (seed.dir() != null && !seed.dir().isBlank()) {
      Path devSeed = Path.of(seed.dir()).resolve(fileName);
      if (Files.exists(devSeed)) {
        return Files.newInputStream(devSeed);
      }
    }
    return getClass().getResourceAsStream("/rules-seed/" + fileName);
  }
}
