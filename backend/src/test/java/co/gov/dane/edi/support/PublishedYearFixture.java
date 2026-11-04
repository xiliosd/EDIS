package co.gov.dane.edi.support;

import co.gov.dane.edi.admin.PublicationService;
import co.gov.dane.edi.admin.QuestionFactsService;
import co.gov.dane.edi.admin.YearCopyService;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.service.QuestionnaireService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Año 2027 publicado para las pruebas de correcciones, paquetes y promoción. Hace tres cosas, cada
 * una por separado para que una prueba pueda detenerse a mitad de camino: crea el borrador con
 * {@link DraftYearFixture}, crea en Oracle las columnas que la copia deja pendientes y publica con
 * el servicio real.
 *
 * <p>La limpieza va en {@code try/finally}: el borrador se quita siempre, aunque falle un {@code
 * DROP COLUMN}. Del historial solo se borran las copias que la propia prueba creó.
 */
public final class PublishedYearFixture {

  public static final String SURVEY_CODE = "EDIN";
  public static final int YEAR = 2027;

  private final DraftYearFixture draft;
  private final JdbcTemplate jdbc;
  private final SurveyProperties survey;
  private final QuestionFactsService factsService;
  private final AnswerColumnRepository answerColumns;
  private final QuestionnaireService questionnaire;
  private final PublicationService publication;
  private final RulesFileRepository rulesFiles;
  private final Map<String, String> createdColumns = new LinkedHashMap<>();
  private final Set<String> historyBefore = new HashSet<>();
  private boolean historyRemembered;

  public PublishedYearFixture(
      JdbcTemplate jdbc,
      RulesFileRepository rulesFiles,
      SurveyProperties survey,
      YearCopyService yearCopy,
      QuestionFactsService factsService,
      AnswerColumnRepository answerColumns,
      QuestionnaireService questionnaire,
      PublicationService publication) {
    this.draft = new DraftYearFixture(jdbc, rulesFiles, survey, yearCopy);
    this.jdbc = jdbc;
    this.rulesFiles = rulesFiles;
    this.survey = survey;
    this.factsService = factsService;
    this.answerColumns = answerColumns;
    this.questionnaire = questionnaire;
    this.publication = publication;
  }

  /** El fixture del borrador, para pedirle los ids de la copia. */
  public DraftYearFixture draft() {
    return draft;
  }

  /** Paso 1: el borrador de 2027, con sus columnas aún pendientes. */
  public void installDraft() {
    rememberHistory();
    draft.install();
    questionnaire.evict();
  }

  /** Pasos 1 a 3: borrador, columnas creadas y año publicado. */
  public void installPublished(String user) {
    installDraft();
    createMissingColumns(YEAR);
    publish(user);
  }

  /** Paso 2: crea en Oracle las columnas que el año aún tiene pendientes. */
  public void createMissingColumns(int year) {
    factsService.facts(SURVEY_CODE, year).values().stream()
        .filter(question -> question.columnStatus() == ColumnStatus.PENDING_COLUMN)
        .filter(question -> !createdColumns.containsKey(question.column()))
        .forEach(
            question -> {
              String table =
                  SurveyPage.find(question.pageCode()).orElseThrow().chapter().tableName();
              jdbc.execute(
                  "ALTER TABLE " + table + " ADD (" + question.column() + " VARCHAR2(50))");
              createdColumns.put(question.column(), table);
              answerColumns.evict(table);
            });
  }

  /** Paso 3: publica el borrador con la versión que tenga. */
  public void publish(String user) {
    publication.publish(SURVEY_CODE, YEAR, rulesFiles.load(SURVEY_CODE, YEAR).version(), user);
  }

  /** Anota el historial existente para borrar después solo lo que cree la prueba. */
  public void rememberHistory() {
    historyBefore.clear();
    historyBefore.addAll(historyNames());
    historyRemembered = true;
  }

  /** Quita columnas, borrador e historial creado; el borrador se quita aunque algo falle antes. */
  public void remove() {
    try {
      dropCreatedColumns();
    } catch (RuntimeException dropFailure) {
      try {
        removeDraftAndHistory();
      } catch (RuntimeException cleanupFailure) {
        dropFailure.addSuppressed(cleanupFailure);
      }
      throw dropFailure;
    }
    removeDraftAndHistory();
  }

  private void removeDraftAndHistory() {
    try {
      try {
        draft.remove();
      } finally {
        deleteNewHistory();
      }
    } finally {
      questionnaire.evict();
      rulesFiles.evict();
    }
  }

  private void dropCreatedColumns() {
    RuntimeException failure = null;
    for (Map.Entry<String, String> column : createdColumns.entrySet()) {
      try {
        jdbc.execute("ALTER TABLE " + column.getValue() + " DROP COLUMN " + column.getKey());
      } catch (RuntimeException exception) {
        if (failure == null) {
          failure = exception;
        } else {
          failure.addSuppressed(exception);
        }
      } finally {
        answerColumns.evict(column.getValue());
      }
    }
    createdColumns.clear();
    if (failure != null) {
      throw failure;
    }
  }

  private Set<String> historyNames() {
    Path history = historyDirectory();
    if (!Files.isDirectory(history)) {
      return Set.of();
    }
    try (Stream<Path> files = Files.list(history)) {
      Set<String> names = new HashSet<>();
      files.forEach(file -> names.add(file.getFileName().toString()));
      return names;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private void deleteNewHistory() {
    if (!historyRemembered) {
      return;
    }
    historyRemembered = false;
    Path history = historyDirectory();
    for (String name : historyNames()) {
      if (!historyBefore.contains(name)) {
        try {
          Files.deleteIfExists(history.resolve(name));
        } catch (IOException exception) {
          throw new UncheckedIOException(exception);
        }
      }
    }
  }

  private Path historyDirectory() {
    return Path.of(survey.rulesDir()).resolve("historial");
  }
}
