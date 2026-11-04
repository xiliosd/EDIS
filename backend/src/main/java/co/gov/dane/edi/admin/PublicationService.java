package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.AnswerTableResolver.TableCheck;
import co.gov.dane.edi.admin.dto.ChecklistItem;
import co.gov.dane.edi.admin.dto.PublicationChecklist;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.rules.AnalysisReport;
import co.gov.dane.edi.rules.CaseResult;
import co.gov.dane.edi.rules.CaseRunner;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.Finding;
import co.gov.dane.edi.rules.Publication;
import co.gov.dane.edi.rules.QuestionFacts;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RuleSetAnalyzer;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.SaveLocks;
import co.gov.dane.edi.service.error.ConflictException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Lista de comprobación y publicación de un año. Publicar exige cuatro condiciones: análisis sin
 * errores, todos los casos en {@code pasa}, todas las preguntas con su columna y todas las tablas
 * del mapeo válidas. Los avisos nunca bloquean: se guardan en {@code publicacion.avisos}.
 *
 * <p>El orden es el de {@code RulesAdminService}: candado global de administración, año en borrador
 * y versión vigente, lista recalculada dentro del candado y, solo si está lista, el guardado. La
 * respuesta se arma sin soltar el candado.
 */
@Service
public class PublicationService {

  static final String SIN_ERRORES = "SIN_ERRORES";
  static final String CASOS_EN_VERDE = "CASOS_EN_VERDE";
  static final String COLUMNAS_CREADAS = "COLUMNAS_CREADAS";
  static final String TABLAS_VALIDAS = "TABLAS_VALIDAS";

  private static final String OPERATION = "publicar";
  private static final int MAX_ELEMENTS = 50;

  private final YearCatalogService years;
  private final RulesFileRepository rulesFiles;
  private final EffectiveRulesService effectiveRules;
  private final QuestionFactsService factsService;
  private final AnswerTableResolver tables;
  private final QuestionnaireService questionnaire;
  private final SaveLocks locks;
  private final Clock clock;

  public PublicationService(
      YearCatalogService years,
      RulesFileRepository rulesFiles,
      EffectiveRulesService effectiveRules,
      QuestionFactsService factsService,
      AnswerTableResolver tables,
      QuestionnaireService questionnaire,
      SaveLocks locks,
      Clock clock) {
    this.years = years;
    this.rulesFiles = rulesFiles;
    this.effectiveRules = effectiveRules;
    this.factsService = factsService;
    this.tables = tables;
    this.questionnaire = questionnaire;
    this.locks = locks;
    this.clock = clock;
  }

  /** Informativa para cualquier año; en uno publicado muestra cómo estaría hoy. */
  public PublicationChecklist checklist(String survey, int year) {
    return checklistOf(survey, year, years.requireYear(survey, year));
  }

  /**
   * Publica el año en borrador si la lista está completa; si no, {@link
   * PublicationBlockedException} con la lista y el archivo queda como estaba.
   */
  public PublicationChecklist publish(String survey, int year, int version, String user) {
    return locks.withAdminLock(
        () -> {
          years.requireDraft(survey, year);
          RuleSet current = years.requireYear(survey, year);
          if (current.version() != version) {
            throw new ConflictException(
                "Otro usuario modificó el cuestionario. Recargue la página.");
          }
          PublicationChecklist before = checklistOf(survey, year, current);
          if (!before.ready()) {
            throw new PublicationBlockedException(before);
          }
          // El guardado sube la versión en uno: esa es la que la constancia debe nombrar.
          Publication publication =
              new Publication(clock.instant(), user, version + 1, before.warnings());
          RuleSet saved;
          try {
            saved =
                rulesFiles.save(
                    current.withState(YearState.PUBLICADO).withPublication(publication),
                    version,
                    user,
                    OPERATION,
                    List.of());
          } finally {
            effectiveRules.evict();
            questionnaire.evict();
            tables.evict();
          }
          return checklistOf(survey, year, saved);
        });
  }

  private PublicationChecklist checklistOf(String survey, int year, RuleSet file) {
    return checklistFor(
        file, factsService.facts(survey, year), effectiveRules.legacySkips(survey, year));
  }

  /**
   * La misma lista para unas reglas y unas preguntas dadas: la importación de un paquete la corre
   * con las reglas que trae y las preguntas de esta base.
   */
  PublicationChecklist checklistFor(
      RuleSet file, Map<String, QuestionFacts> facts, List<SkipRule> legacySkips) {
    RuleSet effective = EffectiveRulesService.merge(file, legacySkips);
    AnalysisReport report = RuleSetAnalyzer.analyze(effective, facts);
    List<CaseResult> results = CaseRunner.runAll(effective, facts);
    List<ChecklistItem> items =
        List.of(noErrors(report), casesInGreen(results), columnsCreated(facts), validTables(file));
    return new PublicationChecklist(
        file.version(),
        items.stream().allMatch(ChecklistItem::passed),
        items,
        report.warnings().stream().map(PublicationService::warningOf).toList());
  }

  private static ChecklistItem noErrors(AnalysisReport report) {
    boolean passed = !report.hasErrors();
    return new ChecklistItem(
        SIN_ERRORES,
        passed,
        passed
            ? "El análisis no tiene errores."
            : report.errors().size() == 1
                ? "El análisis tiene 1 error."
                : "El análisis tiene " + report.errors().size() + " errores.",
        capped(report.errors().stream().map(Finding::element).toList()));
  }

  private static ChecklistItem casesInGreen(List<CaseResult> results) {
    List<String> notGreen =
        results.stream()
            .filter(result -> result.status() != CaseResult.Status.PASA)
            .map(CaseResult::id)
            .toList();
    String detail;
    if (!notGreen.isEmpty()) {
      detail = "Hay casos que fallan o están por revisar.";
    } else if (results.isEmpty()) {
      detail = "No hay casos de prueba.";
    } else {
      detail =
          results.size() == 1
              ? "El caso de prueba pasa."
              : "Los " + results.size() + " casos de prueba pasan.";
    }
    return new ChecklistItem(CASOS_EN_VERDE, notGreen.isEmpty(), detail, capped(notGreen));
  }

  private static ChecklistItem columnsCreated(Map<String, QuestionFacts> facts) {
    List<String> pending =
        facts.values().stream()
            .filter(question -> question.columnStatus() == ColumnStatus.PENDING_COLUMN)
            .map(QuestionFacts::column)
            .toList();
    return new ChecklistItem(
        COLUMNAS_CREADAS,
        pending.isEmpty(),
        pending.isEmpty()
            ? "Todas las preguntas activas tienen su columna."
            : (pending.size() == 1 ? "Falta 1 columna" : "Faltan " + pending.size() + " columnas")
                + "; descargue el script.",
        capped(pending));
  }

  private ChecklistItem validTables(RuleSet file) {
    List<String> invalid = new ArrayList<>();
    for (Chapter chapter : Chapter.values()) {
      String mapped = file.tables().get(chapter.code());
      String table = mapped == null ? chapter.tableName() : mapped;
      if (tables.check(table) != TableCheck.VALID) {
        invalid.add(chapter.code());
      }
    }
    return new ChecklistItem(
        TABLAS_VALIDAS,
        invalid.isEmpty(),
        invalid.isEmpty()
            ? "Todas las tablas del mapeo son válidas."
            : "Hay tablas del mapeo que no existen o no son válidas.",
        capped(invalid));
  }

  private static String warningOf(Finding finding) {
    return finding.code() + "@" + finding.element();
  }

  private static List<String> capped(List<String> elements) {
    return elements.size() > MAX_ELEMENTS ? elements.subList(0, MAX_ELEMENTS) : elements;
  }
}
