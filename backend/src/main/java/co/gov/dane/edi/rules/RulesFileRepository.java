package co.gov.dane.edi.rules;

import co.gov.dane.edi.admin.AdminAudit;
import co.gov.dane.edi.config.RulesSeedProperties;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.service.error.ConflictException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Archivos de reglas en {@code RULES_DIR/<CODIGO>-<AÑO>.json}, uno por año (spec §5.3). Si el del
 * año activo no existe se copia la semilla (de {@code RULES_SEED_DIR} en desarrollo, del classpath
 * en producción). Una sola instancia (D6) permite cachear cada año en memoria; las escrituras dejan
 * copia en {@code historial/} y reemplazan el archivo de forma atómica.
 */
@Repository
public class RulesFileRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(RulesFileRepository.class);
  private static final Pattern SURVEY_CODE = Pattern.compile("[A-Z]{4}");
  private static final String HISTORY_DIR = "historial";

  private final JsonMapper jsonMapper;
  private final SurveyProperties survey;
  private final RulesSeedProperties seed;
  private final AdminAudit audit;
  private final Map<String, RuleSet> cache = new ConcurrentHashMap<>();

  public RulesFileRepository(
      JsonMapper jsonMapper, SurveyProperties survey, RulesSeedProperties seed, AdminAudit audit) {
    this.jsonMapper = jsonMapper;
    this.survey = survey;
    this.seed = seed;
    this.audit = audit;
  }

  /** Reglas de un año; si no hay archivo se siembra (o se usan reglas vacías sin semilla). */
  public RuleSet load(String surveyCode, int year) {
    String key = fileName(surveyCode, year);
    RuleSet current = cache.get(key);
    if (current != null) {
      return current;
    }
    RuleSet loaded = readOrSeed(surveyCode, year);
    RuleSetValidator.validate(loaded);
    return cache.computeIfAbsent(key, ignored -> loaded);
  }

  /** Reglas de un año solo si su archivo existe: nunca siembra. */
  public Optional<RuleSet> find(String surveyCode, int year) {
    String key = fileName(surveyCode, year);
    RuleSet current = cache.get(key);
    if (current != null) {
      return Optional.of(current);
    }
    if (Files.notExists(rulesDir().resolve(key))) {
      return Optional.empty();
    }
    return Optional.of(load(surveyCode, year));
  }

  /** Años que tienen archivo para la encuesta, en orden ascendente. */
  public List<Integer> years(String surveyCode) {
    fileName(surveyCode, survey.year());
    Pattern name = Pattern.compile(Pattern.quote(surveyCode) + "-(\\d{4})\\.json");
    Path dir = rulesDir();
    if (Files.notExists(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .map(file -> name.matcher(file.getFileName().toString()))
          .filter(Matcher::matches)
          .map(matcher -> Integer.parseInt(matcher.group(1)))
          .sorted()
          .toList();
    } catch (IOException exception) {
      throw new UncheckedIOException("No fue posible listar los años de " + surveyCode, exception);
    }
  }

  /**
   * Guarda las reglas con control de versión (spec §5.3): copia el archivo vigente al historial,
   * escribe un temporal con la versión + 1 y lo mueve de forma atómica. {@code reason} es la clave
   * corta de la operación que queda en la auditoría. Si el año aún no tiene archivo, la versión
   * esperada es 0 y no hay historial que copiar.
   */
  public synchronized RuleSet save(RuleSet rules, int expectedVersion, String user, String reason) {
    RuleSetValidator.validate(rules);
    String name = fileName(rules.survey(), rules.year());
    Path file = rulesDir().resolve(name);
    int currentVersion = find(rules.survey(), rules.year()).map(RuleSet::version).orElse(0);
    if (currentVersion != expectedVersion) {
      throw new ConflictException("Otro usuario modificó el cuestionario. Recargue la página.");
    }
    RuleSet toSave = rules.withVersion(currentVersion + 1);
    Path temporary = null;
    try {
      Files.createDirectories(file.getParent());
      if (Files.exists(file)) {
        Path history = Files.createDirectories(file.getParent().resolve(HISTORY_DIR));
        String historyName =
            name.substring(0, name.length() - ".json".length())
                + "."
                + System.currentTimeMillis()
                + ".json";
        Files.copy(file, history.resolve(historyName), StandardCopyOption.REPLACE_EXISTING);
      }
      temporary = Files.createTempFile(file.getParent(), name, ".tmp");
      writeFile(temporary, toSave);
      moveAtomically(temporary, file);
    } catch (IOException exception) {
      throw new UncheckedIOException("No fue posible guardar las reglas " + name, exception);
    } finally {
      deleteQuietly(temporary);
    }
    cache.put(name, toSave);
    audit.record(user, toSave.survey(), toSave.year(), reason, List.of());
    return toSave;
  }

  /** Descarta la caché para releer los archivos del disco. */
  public void evict() {
    cache.clear();
  }

  /** Punto de extensión de las pruebas para simular un fallo a mitad de la escritura. */
  void writeFile(Path file, RuleSet rules) throws IOException {
    try {
      jsonMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), rules);
    } catch (JacksonException exception) {
      throw new IOException("No fue posible serializar las reglas", exception);
    }
  }

  private static void moveAtomically(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      // Sistemas de archivos sin movimiento atómico: se conserva el reemplazo del archivo.
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteQuietly(Path temporary) {
    if (temporary == null) {
      return;
    }
    try {
      Files.deleteIfExists(temporary);
    } catch (IOException exception) {
      LOGGER.warn("No fue posible borrar el temporal {}", temporary.getFileName());
    }
  }

  private Path rulesDir() {
    return Path.of(survey.rulesDir());
  }

  /** El código se valida porque forma parte de una ruta de archivo. */
  private static String fileName(String surveyCode, int year) {
    if (surveyCode == null || !SURVEY_CODE.matcher(surveyCode).matches()) {
      throw new IllegalArgumentException("Código de encuesta inválido");
    }
    return surveyCode + "-" + year + ".json";
  }

  private RuleSet readOrSeed(String surveyCode, int year) {
    String fileName = fileName(surveyCode, year);
    Path file = rulesDir().resolve(fileName);
    try {
      boolean seeded = false;
      if (Files.notExists(file)) {
        Files.createDirectories(file.getParent());
        try (InputStream seedContent = openSeed(fileName)) {
          if (seedContent == null) {
            LOGGER.warn("Sin semilla de reglas para {}; se usan reglas vacías", fileName);
            return RuleSet.empty(surveyCode, year);
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
