package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.YearCopyRepository.CopiedYear;
import co.gov.dane.edi.admin.dto.YearSummary;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RuleSetValidator;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.SaveLocks;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.InvalidEditException;
import co.gov.dane.edi.service.error.YearCreationFailedException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Crea el cuestionario de un año nuevo a partir de otro, todo o nada: bajo el candado de
 * administración ({@code SaveLocks.withAdminLock}), copia las filas FRM_* en una transacción y, ya
 * confirmada, escribe el archivo de reglas del año (en borrador). Si el archivo no se puede
 * escribir se borran las filas copiadas.
 *
 * <p>Los saltos heredados de FRM_SALTO del año de origen pasan al archivo como reglas {@code
 * legacy-<COL>}: el año nuevo no tiene filas en FRM_SALTO y sus saltos se editan solo en el
 * archivo.
 */
@Service
public class YearCopyService {

  private static final Logger LOGGER = LoggerFactory.getLogger(YearCopyService.class);
  private static final String OPERATION = "crear-anio";
  private static final int MIN_YEAR = 2020;
  private static final int MAX_YEAR = 2100;

  private final YearCatalogService years;
  private final RulesFileRepository rulesFiles;
  private final YearCopyRepository copies;
  private final EffectiveRulesService effectiveRules;
  private final QuestionnaireService questionnaire;
  private final AnswerTableResolver tables;
  private final SaveLocks locks;
  private final TransactionTemplate transaction;

  public YearCopyService(
      YearCatalogService years,
      RulesFileRepository rulesFiles,
      YearCopyRepository copies,
      EffectiveRulesService effectiveRules,
      QuestionnaireService questionnaire,
      AnswerTableResolver tables,
      SaveLocks locks,
      PlatformTransactionManager transactionManager) {
    this.years = years;
    this.rulesFiles = rulesFiles;
    this.copies = copies;
    this.effectiveRules = effectiveRules;
    this.questionnaire = questionnaire;
    this.tables = tables;
    this.locks = locks;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /**
   * @throws ConflictException si el año ya existe (archivo o filas) o la encuesta ya tiene borrador
   * @throws YearCreationFailedException si no se pudo escribir el archivo (las filas se borran)
   */
  public YearSummary createYear(String survey, int year, int fromYear, String user) {
    years.requireSurvey(survey);
    if (year < MIN_YEAR || year > MAX_YEAR) {
      throw new InvalidEditException("year", "El año debe estar entre 2020 y 2100.");
    }
    return locks.withAdminLock(
        () -> {
          requireNewYear(survey, year);
          Optional<Integer> draft = years.draftYear(survey);
          if (draft.isPresent()) {
            throw new ConflictException("Ya hay un borrador abierto (" + draft.get() + ").");
          }
          years.requireYear(survey, fromYear);
          if (copies.periodCount(survey, fromYear) == 0) {
            throw new InvalidEditException("fromYear", "El año de origen no tiene preguntas.");
          }
          // Antes de leer las reglas: un PROY inexacto también duplica preguntas en la lectura.
          // La copia lo vuelve a comprobar dentro de su transacción.
          copies.requireExactProject(survey, fromYear);
          RuleSet draftRules =
              draftRulesFrom(effectiveRules.effectiveRules(survey, fromYear), year);
          RuleSetValidator.validate(draftRules);
          CopiedYear copied;
          try {
            copied = transaction.execute(status -> copies.copy(survey, fromYear, year));
          } finally {
            evictCaches();
          }
          try {
            rulesFiles.save(
                draftRules, 0, user, OPERATION, List.of(String.valueOf(year), "desde-" + fromYear));
          } catch (ConflictException conflict) {
            // El archivo ya existía antes de este guardado: no es nuestro y no se borra.
            deleteRows(survey, year, copied, conflict);
            throw conflict;
          } catch (RuntimeException exception) {
            compensate(survey, year, copied, exception);
          } finally {
            evictCaches();
          }
          return years.years(survey).stream()
              .filter(summary -> summary.year() == year)
              .findFirst()
              .orElseThrow();
        });
  }

  /** Un año existe si tiene archivo, es el activo o ya tiene preguntas en la base. */
  private void requireNewYear(String survey, int year) {
    boolean exists =
        years.years(survey).stream().anyMatch(summary -> summary.year() == year)
            || copies.periodCount(survey, year) > 0;
    if (exists) {
      throw new ConflictException("Ya existe un cuestionario para " + year + ".");
    }
  }

  /**
   * Reglas efectivas del año de origen (archivo más saltos heredados ya combinados) como borrador
   * del año nuevo. Las columnas nuevas no se copian: ya existen en las tablas o siguen pendientes
   * en el año de origen, y el año nuevo empieza sin definiciones propias.
   */
  private static RuleSet draftRulesFrom(RuleSet source, int year) {
    return new RuleSet(
        source.survey(),
        year,
        0,
        source.skips(),
        source.validations(),
        YearState.BORRADOR,
        source.tables(),
        Map.of());
  }

  /**
   * Sin archivo el año no existe: se borra el archivo si alcanzó a escribirse (la falla pudo ser
   * posterior) y las filas ya confirmadas, y se informa la causa.
   */
  private void compensate(String survey, int year, CopiedYear copied, RuntimeException cause) {
    String detail =
        "No fue posible guardar el archivo de reglas de " + year + "; el año no se creó.";
    try {
      rulesFiles.discardNewYear(survey, year);
    } catch (RuntimeException discardFailure) {
      LOGGER.error("No fue posible borrar el archivo de {} {}", survey, year, discardFailure);
    }
    deleteRows(survey, year, copied, cause);
    throw new YearCreationFailedException(detail + " Intente de nuevo.", cause);
  }

  private void deleteRows(String survey, int year, CopiedYear copied, RuntimeException cause) {
    try {
      transaction.executeWithoutResult(status -> copies.delete(copied));
    } catch (RuntimeException deleteFailure) {
      LOGGER.error(
          "No fue posible borrar las filas copiadas de {} {} tras fallar el archivo",
          survey,
          year,
          deleteFailure);
      throw new YearCreationFailedException(
          "No fue posible guardar el archivo de reglas de "
              + year
              + "; el año no se creó. Además quedaron filas copiadas en la base: avise a la"
              + " persona técnica.",
          cause);
    }
  }

  private void evictCaches() {
    questionnaire.evict();
    effectiveRules.evict();
    tables.evict();
  }
}
