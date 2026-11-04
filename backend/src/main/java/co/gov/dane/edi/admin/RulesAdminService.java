package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.QuestionFactsService.Snapshot;
import co.gov.dane.edi.admin.dto.RulesEditResult;
import co.gov.dane.edi.admin.dto.RulesEditorView;
import co.gov.dane.edi.admin.dto.SimulatorView;
import co.gov.dane.edi.model.PageDetail;
import co.gov.dane.edi.model.RespondentRules;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.rules.AnalysisCodes;
import co.gov.dane.edi.rules.AnalysisReport;
import co.gov.dane.edi.rules.CaseResult;
import co.gov.dane.edi.rules.CaseRunner;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.FieldValidation;
import co.gov.dane.edi.rules.Finding;
import co.gov.dane.edi.rules.QuestionFacts;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RuleSetAnalyzer;
import co.gov.dane.edi.rules.RuleSetValidator;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.rules.TestCase;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.SaveLocks;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.InvalidEditException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Edición de reglas de salto, validaciones y casos de prueba de un año en borrador o, como
 * corrección con motivo, de un año publicado. Los cambios no tocan las tablas FRM_*, solo el
 * archivo de reglas, por eso no hay transacción de base de datos.
 *
 * <p>Cada cambio sigue el orden de {@code QuestionAdminService}: candado global de administración;
 * año en borrador y versión vigente; reglas candidatas y su análisis; y solo si no agregan errores
 * que el archivo vigente no tenía, el guardado. La respuesta se arma sin soltar el candado.
 */
@Service
public class RulesAdminService {

  /** Un id nuevo de regla o caso: el límite de 40 caracteres es el de los códigos de auditoría. */
  private static final Pattern NEW_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");

  /** Lo que admite un código de auditoría: los ids importados deben poder auditarse al editarse. */
  private static final Pattern AUDITABLE_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

  private static final Pattern COLUMN = Pattern.compile("[A-Z][0-9A-Z_]{1,29}");

  private final YearCatalogService years;
  private final RulesFileRepository rulesFiles;
  private final EffectiveRulesService effectiveRules;
  private final QuestionFactsService factsService;
  private final QuestionnaireService questionnaire;
  private final SaveLocks locks;
  private final JsonMapper jsonMapper;

  public RulesAdminService(
      YearCatalogService years,
      RulesFileRepository rulesFiles,
      EffectiveRulesService effectiveRules,
      QuestionFactsService factsService,
      QuestionnaireService questionnaire,
      SaveLocks locks,
      JsonMapper jsonMapper) {
    this.years = years;
    this.rulesFiles = rulesFiles;
    this.effectiveRules = effectiveRules;
    this.factsService = factsService;
    this.questionnaire = questionnaire;
    this.locks = locks;
    this.jsonMapper = jsonMapper;
  }

  /** Lectura para cualquier año, también los publicados (solo lectura en el editor). */
  public RulesEditorView view(String survey, int year) {
    RuleSet file = years.requireYear(survey, year);
    Snapshot snapshot = factsService.snapshot(survey, year);
    RuleSet effective = EffectiveRulesService.merge(file, effectiveRules.legacySkips(survey, year));
    Set<String> fileIds = file.skips().stream().map(SkipRule::id).collect(Collectors.toSet());
    return new RulesEditorView(
        survey,
        year,
        file.effectiveState(),
        file.version(),
        effective.skips(),
        effective.skips().stream().map(SkipRule::id).filter(id -> !fileIds.contains(id)).toList(),
        file.validations(),
        file.cases(),
        RuleSetAnalyzer.analyze(effective, snapshot.facts()),
        CaseRunner.runAll(effective, snapshot.facts()),
        snapshot.questions());
  }

  /**
   * Reglas efectivas y páginas del año para el simulador; también para años publicados. Las páginas
   * se arman como las ve el encuestado, pero sin estado ni respuestas.
   */
  public SimulatorView simulator(String survey, int year) {
    RuleSet file = years.requireYear(survey, year);
    RuleSet effective = EffectiveRulesService.merge(file, effectiveRules.legacySkips(survey, year));
    List<PageDetail> pages =
        SurveyPage.ordered().stream()
            .map(
                page ->
                    new PageDetail(
                        page.code(),
                        page.chapter().code(),
                        page.chapter().title(),
                        page.chapter().module(),
                        null,
                        questionnaire.pageEntries(page, survey, year),
                        Map.of()))
            .toList();
    return new SimulatorView(file.version(), RespondentRules.from(effective), pages);
  }

  /** El archivo del año tal como está guardado, listo para descargar. */
  public byte[] export(String survey, int year) {
    RuleSet file = years.requireYear(survey, year);
    try {
      return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(file);
    } catch (JacksonException exception) {
      throw new IllegalStateException("No fue posible serializar las reglas", exception);
    }
  }

  public RulesEditResult saveSkip(
      String survey,
      int year,
      String ruleId,
      SkipRule rule,
      int version,
      String user,
      String reason) {
    if (!ruleId.equals(rule.id())) {
      throw new InvalidEditException("El id de la regla no coincide.");
    }
    return apply(
        survey,
        year,
        version,
        user,
        reason,
        "editar-salto",
        List.of(ruleId),
        (current, legacy) -> {
          boolean known =
              current.skips().stream().anyMatch(existing -> existing.id().equals(ruleId))
                  || legacy.stream().anyMatch(existing -> existing.id().equals(ruleId));
          if (!known && !NEW_ID.matcher(ruleId).matches()) {
            throw new InvalidEditException(
                "El id de la regla solo admite minúsculas, números y guiones (hasta 40).");
          }
          return current.withSkips(upsert(current.skips(), rule, SkipRule::id));
        });
  }

  public RulesEditResult deleteSkip(
      String survey, int year, String ruleId, int version, String user, String reason) {
    return apply(
        survey,
        year,
        version,
        user,
        reason,
        "borrar-salto",
        List.of(ruleId),
        (current, legacy) -> {
          if (current.skips().stream().noneMatch(existing -> existing.id().equals(ruleId))) {
            throw new ResourceNotFoundException("La regla no existe.");
          }
          return current.withSkips(
              current.skips().stream().filter(existing -> !existing.id().equals(ruleId)).toList());
        });
  }

  public RulesEditResult saveValidation(
      String survey,
      int year,
      String column,
      FieldValidation validation,
      int version,
      String user,
      String reason) {
    requireColumn(column);
    return apply(
        survey,
        year,
        version,
        user,
        reason,
        "editar-validacion",
        List.of(column),
        (current, legacy) -> {
          Map<String, FieldValidation> validations = new LinkedHashMap<>(current.validations());
          validations.put(column, validation);
          return current.withValidations(validations);
        });
  }

  public RulesEditResult deleteValidation(
      String survey, int year, String column, int version, String user, String reason) {
    requireColumn(column);
    return apply(
        survey,
        year,
        version,
        user,
        reason,
        "borrar-validacion",
        List.of(column),
        (current, legacy) -> {
          if (!current.validations().containsKey(column)) {
            throw new ResourceNotFoundException("La validación no existe.");
          }
          Map<String, FieldValidation> validations = new LinkedHashMap<>(current.validations());
          validations.remove(column);
          return current.withValidations(validations);
        });
  }

  public RulesEditResult saveCase(
      String survey,
      int year,
      String caseId,
      TestCase testCase,
      int version,
      String user,
      String reason) {
    if (!caseId.equals(testCase.id())) {
      throw new InvalidEditException("El id del caso no coincide.");
    }
    // Guardar un caso lo da por revisado: sus respuestas ya describen el cuestionario actual.
    TestCase reviewed = testCase.withNeedsReview(false);
    return apply(
        survey,
        year,
        version,
        user,
        reason,
        "editar-caso",
        List.of(caseId),
        (current, legacy) -> {
          boolean known =
              current.cases().stream().anyMatch(existing -> existing.id().equals(caseId));
          if (!known && !NEW_ID.matcher(caseId).matches()) {
            throw new InvalidEditException(
                "El id del caso solo admite minúsculas, números y guiones (hasta 40).");
          }
          return current.withCases(upsert(current.cases(), reviewed, TestCase::id));
        });
  }

  public RulesEditResult deleteCase(
      String survey, int year, String caseId, int version, String user, String reason) {
    return apply(
        survey,
        year,
        version,
        user,
        reason,
        "borrar-caso",
        List.of(caseId),
        (current, legacy) -> {
          if (current.cases().stream().noneMatch(existing -> existing.id().equals(caseId))) {
            throw new ResourceNotFoundException("El caso no existe.");
          }
          return current.withCases(
              current.cases().stream().filter(existing -> !existing.id().equals(caseId)).toList());
        });
  }

  /** Reemplaza solo saltos, validaciones y casos: el estado y las tablas del año no se importan. */
  public RulesEditResult importRules(
      String survey, int year, RuleSet imported, int version, String user, String reason) {
    if (!survey.equals(imported.survey()) || year != imported.year()) {
      throw new InvalidEditException("El archivo no corresponde a la encuesta y año indicados.");
    }
    boolean badId =
        imported.skips().stream().map(SkipRule::id).anyMatch(RulesAdminService::notAuditable)
            || imported.cases().stream()
                .map(TestCase::id)
                .anyMatch(RulesAdminService::notAuditable);
    if (badId) {
      throw new InvalidEditException(
          "Los ids de reglas y casos admiten letras, números, guion y guion bajo (hasta 40).");
    }
    return apply(
        survey,
        year,
        version,
        user,
        reason,
        "importar-reglas",
        List.of(),
        (current, legacy) ->
            current
                .withSkips(imported.skips())
                .withValidations(imported.validations())
                .withCases(imported.cases()));
  }

  /**
   * Cambia el archivo del año con el candado de administración (ver la descripción de la clase).
   */
  private RulesEditResult apply(
      String survey,
      int year,
      int version,
      String user,
      String reason,
      String operation,
      List<String> codes,
      CandidateBuilder build) {
    return locks.withAdminLock(
        () -> {
          // Sobre un año publicado esto es una corrección: lleva motivo y su propia operación.
          String storedReason = years.requireEditable(survey, year, EditKind.CORRECTION, reason);
          String audited =
              storedReason == null ? operation : EditKind.correctionOperation(operation);
          RuleSet current = years.requireYear(survey, year);
          if (current.version() != version) {
            throw new ConflictException(
                "Otro usuario modificó el cuestionario. Recargue la página.");
          }
          List<SkipRule> legacy = effectiveRules.legacySkips(survey, year);
          RuleSet candidate = build.build(current, legacy);
          RuleSet effectiveCandidate = EffectiveRulesService.merge(candidate, legacy);
          rejectOversized(candidate, effectiveCandidate);
          Map<String, QuestionFacts> facts = factsService.facts(survey, year);
          AnalysisReport after = RuleSetAnalyzer.analyze(effectiveCandidate, facts);
          rejectStructuralProblems(candidate, after);
          RuleSet effectiveCurrent = EffectiveRulesService.merge(current, legacy);
          // Un año vigente fuera del tope no tiene un análisis con qué comparar (solo el error de
          // tamaño): basta con que el candidato quede dentro del tope.
          if (!RuleSetAnalyzer.exceedsSizeLimits(effectiveCurrent)) {
            AnalysisReport before = RuleSetAnalyzer.analyze(effectiveCurrent, facts);
            List<Finding> added = after.newErrorsComparedTo(before);
            if (!added.isEmpty()) {
              throw new InvalidRulesException(new AnalysisReport(added, after.warnings()));
            }
          }
          List<CaseResult> caseResults = CaseRunner.runAll(effectiveCandidate, facts);
          if (storedReason != null) {
            rejectCasesTurnedRed(CaseRunner.runAll(effectiveCurrent, facts), caseResults);
          }
          RuleSet saved = rulesFiles.save(candidate, version, user, audited, codes, storedReason);
          effectiveRules.evict();
          return new RulesEditResult(saved.version(), after, caseResults);
        });
  }

  /**
   * Una corrección no puede dejar en rojo un caso que pasaba: las reglas de un año publicado son
   * las que sus pruebas daban por buenas. Los casos que queden por revisar no bloquean.
   */
  private static void rejectCasesTurnedRed(List<CaseResult> before, List<CaseResult> after) {
    Set<String> passing =
        before.stream()
            .filter(result -> result.status() == CaseResult.Status.PASA)
            .map(CaseResult::id)
            .collect(Collectors.toSet());
    List<String> turnedRed =
        after.stream()
            .filter(result -> result.status() == CaseResult.Status.FALLA)
            .map(CaseResult::id)
            .filter(passing::contains)
            .toList();
    if (!turnedRed.isEmpty()) {
      throw new InvalidEditException(
          "La corrección hace fallar casos de prueba: " + String.join(", ", turnedRed) + ".");
    }
  }

  /**
   * El tope se revisa antes de analizar: el del archivo con el mensaje del validador y el del año
   * (archivo más heredadas de FRM_SALTO), que el archivo por sí solo no ve.
   */
  private static void rejectOversized(RuleSet candidate, RuleSet effectiveCandidate) {
    List<String> problems = new ArrayList<>();
    if (candidate.skips().size() > RuleSetValidator.MAX_SKIPS
        || candidate.cases().size() > RuleSetValidator.MAX_CASES) {
      problems.addAll(RuleSetValidator.problems(candidate));
    } else if (RuleSetAnalyzer.exceedsSizeLimits(effectiveCandidate)) {
      problems.add(RuleSetAnalyzer.OVERSIZED_YEAR_MESSAGE);
    }
    if (!problems.isEmpty()) {
      List<Finding> findings = new ArrayList<>();
      problems.forEach(
          problem ->
              findings.add(new Finding(AnalysisCodes.ESTRUCTURA, "archivo", problem, List.of())));
      throw new InvalidRulesException(new AnalysisReport(findings, List.of()));
    }
  }

  /** El archivo no puede guardarse con problemas de estructura, aunque ya hubiera otros. */
  private static void rejectStructuralProblems(RuleSet candidate, AnalysisReport after) {
    List<String> problems = RuleSetValidator.problems(candidate);
    if (problems.isEmpty()) {
      return;
    }
    List<Finding> findings = new ArrayList<>();
    problems.forEach(
        problem ->
            findings.add(new Finding(AnalysisCodes.ESTRUCTURA, "archivo", problem, List.of())));
    throw new InvalidRulesException(new AnalysisReport(findings, after.warnings()));
  }

  /** Un id nulo lo reporta el validador estructural; aquí solo importa poder auditarlo. */
  private static boolean notAuditable(String id) {
    return id != null && !AUDITABLE_ID.matcher(id).matches();
  }

  private static void requireColumn(String column) {
    if (!COLUMN.matcher(column).matches()) {
      throw new InvalidEditException("La columna no es válida.");
    }
  }

  /** Reemplaza el elemento con el mismo id en su lugar o, si es nuevo, lo agrega al final. */
  private static <T> List<T> upsert(List<T> items, T item, Function<T, String> idOf) {
    List<T> updated = new ArrayList<>();
    boolean replaced = false;
    for (T existing : items) {
      if (idOf.apply(existing).equals(idOf.apply(item))) {
        updated.add(item);
        replaced = true;
      } else {
        updated.add(existing);
      }
    }
    if (!replaced) {
      updated.add(item);
    }
    return updated;
  }

  @FunctionalInterface
  private interface CandidateBuilder {
    RuleSet build(RuleSet current, List<SkipRule> legacy);
  }
}
