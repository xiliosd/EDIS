package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.operation.OperationProperties;
import co.gov.dane.edi.operation.report.dto.CutSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cortes en {@code CUTS_DIR/<anio>/<id>.json}, uno por archivo y nunca modificados: un segundo
 * corte del mismo día es otro archivo. Se escribe en un temporal y se mueve de forma atómica para
 * que una lectura concurrente nunca vea un JSON a medias.
 */
@Repository
public class CoverageCutStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(CoverageCutStore.class);
  private static final Pattern CUT_ID = Pattern.compile("[0-9]{8}-[0-9]{6}-[0-9a-f]{6}");
  private static final Pattern YEAR_DIRECTORY = Pattern.compile("[0-9]{4}");
  private static final String EXTENSION = ".json";

  private final OperationProperties properties;
  private final JsonMapper jsonMapper;

  public CoverageCutStore(OperationProperties properties, JsonMapper jsonMapper) {
    this.properties = properties;
    this.jsonMapper = jsonMapper;
  }

  /** El id forma parte de una ruta: se valida antes de tocar el disco. */
  public static boolean isValidId(String id) {
    return id != null && CUT_ID.matcher(id).matches();
  }

  public void save(CoverageCut cut) {
    if (!isValidId(cut.id())) {
      throw new IllegalArgumentException("Identificador de corte inválido");
    }
    Path yearDirectory = root().resolve(String.valueOf(cut.year()));
    Path temporary = null;
    try {
      Files.createDirectories(yearDirectory);
      temporary = Files.createTempFile(yearDirectory, cut.id(), ".tmp");
      jsonMapper.writeValue(temporary.toFile(), cut);
      moveAtomically(temporary, yearDirectory.resolve(cut.id() + EXTENSION));
    } catch (IOException exception) {
      throw new UncheckedIOException("No fue posible guardar el corte " + cut.id(), exception);
    } finally {
      deleteQuietly(temporary);
    }
  }

  /**
   * Cortes del año, el más reciente primero. {@code departments} nulo no filtra (alcance nacional);
   * una colección vacía no devuelve nada.
   */
  public List<CutSummary> list(int year, Collection<Integer> departments) {
    Path yearDirectory = root().resolve(String.valueOf(year));
    if (Files.notExists(yearDirectory)) {
      return List.of();
    }
    List<CutSummary> summaries = new ArrayList<>();
    try (Stream<Path> files = Files.list(yearDirectory)) {
      files
          .filter(file -> isCutFile(file.getFileName().toString()))
          .forEach(
              file ->
                  read(file)
                      .filter(
                          cut -> departments == null || departments.contains(cut.departmentCode()))
                      .map(CoverageCut::summary)
                      .ifPresent(summaries::add));
    } catch (IOException exception) {
      throw new UncheckedIOException("No fue posible listar los cortes", exception);
    }
    summaries.sort(
        Comparator.comparing(CutSummary::generatedAt).thenComparing(CutSummary::id).reversed());
    return List.copyOf(summaries);
  }

  /** Un id inválido o desconocido da vacío; el id no lleva el año, así que se busca en cada uno. */
  public Optional<CoverageCut> find(String id) {
    if (!isValidId(id)) {
      return Optional.empty();
    }
    Path root = root();
    if (Files.notExists(root)) {
      return Optional.empty();
    }
    try (Stream<Path> directories = Files.list(root)) {
      return directories
          .filter(directory -> YEAR_DIRECTORY.matcher(directory.getFileName().toString()).matches())
          .map(directory -> directory.resolve(id + EXTENSION))
          .filter(Files::isRegularFile)
          .findFirst()
          .flatMap(this::read);
    } catch (IOException exception) {
      throw new UncheckedIOException("No fue posible buscar el corte " + id, exception);
    }
  }

  private static boolean isCutFile(String name) {
    return name.endsWith(EXTENSION)
        && isValidId(name.substring(0, name.length() - EXTENSION.length()));
  }

  /** Un archivo ilegible se omite con un aviso sin su contenido. */
  private Optional<CoverageCut> read(Path file) {
    try {
      return Optional.of(jsonMapper.readValue(file.toFile(), CoverageCut.class));
    } catch (JacksonException exception) {
      LOGGER.warn("Corte ilegible {}", file.getFileName());
      return Optional.empty();
    }
  }

  private Path root() {
    return Path.of(properties.cutsDir());
  }

  private static void moveAtomically(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      LOGGER.warn("El sistema de archivos no soporta movimiento atómico; se mueve sin él");
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
}
