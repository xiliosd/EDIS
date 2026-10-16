package co.gov.dane.edi.rules;

import co.gov.dane.edi.config.RulesSeedProperties;
import co.gov.dane.edi.config.SurveyProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
      if (Files.notExists(file)) {
        Files.createDirectories(file.getParent());
        try (InputStream seedContent = openSeed(fileName)) {
          if (seedContent == null) {
            LOGGER.warn("Sin semilla de reglas para {}; se usan reglas vacías", fileName);
            return RuleSet.empty(survey.code(), survey.year());
          }
          Files.copy(seedContent, file);
          LOGGER.info("Reglas sembradas en {}", fileName);
        }
      }
      return jsonMapper.readValue(file.toFile(), RuleSet.class);
    } catch (JacksonException exception) {
      // El contenido no se registra: puede ser grande y no aporta al diagnóstico.
      LOGGER.error("Archivo de reglas inválido: {}", fileName);
      throw new IllegalStateException("Archivo de reglas inválido: " + fileName, exception);
    } catch (IOException exception) {
      throw new UncheckedIOException("No fue posible leer las reglas " + fileName, exception);
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
