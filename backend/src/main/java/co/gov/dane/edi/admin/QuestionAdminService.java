package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.FrmLookupRepository.CapProyRef;
import co.gov.dane.edi.admin.dto.AdminEditResult;
import co.gov.dane.edi.admin.dto.AdminEntry;
import co.gov.dane.edi.admin.dto.AdminPage;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.QuestionnaireService.PageRow;
import co.gov.dane.edi.service.SaveLocks;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.InvalidEditException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import co.gov.dane.edi.util.ColumnCodeResolver;
import co.gov.dane.edi.util.QuestionTextSanitizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Edición de textos, encabezados, ayudas y orden de un año en borrador (copia al escribir).
 *
 * <p>Orden de cada cambio: candado global {@code admin:frm}; año en borrador y versión vigente; y
 * dentro de una sola transacción, las escrituras FRM_* y al final el guardado del archivo del año
 * (que sube su versión). Si el archivo no se puede guardar la transacción se revierte, así la base
 * y la versión no divergen por una falla de disco; solo un fallo del COMMIT, muy poco probable,
 * dejaría la versión subida sin cambio de datos, algo inocuo. Las cachés se descartan siempre.
 */
@Service
public class QuestionAdminService {

  private static final String LOCK_KEY = "admin:frm";
  private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
  private static final int MAX_NORMALIZATION_PASSES = 5;

  private final YearCatalogService years;
  private final RulesFileRepository rulesFiles;
  private final FrmWriteRepository writes;
  private final FrmLookupRepository lookup;
  private final QuestionnaireAdminService view;
  private final QuestionnaireService questionnaire;
  private final EffectiveRulesService effectiveRules;
  private final AnswerTableResolver tables;
  private final SaveLocks locks;
  private final TransactionTemplate transaction;

  public QuestionAdminService(
      YearCatalogService years,
      RulesFileRepository rulesFiles,
      FrmWriteRepository writes,
      FrmLookupRepository lookup,
      QuestionnaireAdminService view,
      QuestionnaireService questionnaire,
      EffectiveRulesService effectiveRules,
      AnswerTableResolver tables,
      SaveLocks locks,
      PlatformTransactionManager transactionManager) {
    this.years = years;
    this.rulesFiles = rulesFiles;
    this.writes = writes;
    this.lookup = lookup;
    this.view = view;
    this.questionnaire = questionnaire;
    this.effectiveRules = effectiveRules;
    this.tables = tables;
    this.locks = locks;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  public AdminEditResult editQuestionText(
      String survey, int year, long capProyId, String text, int version, String user) {
    String plain = normalize(text);
    years.requireDraft(survey, year);
    CapProyRef ref = requireCapProy(capProyId, survey, year);
    String yearText = String.valueOf(year);
    return apply(
        survey,
        year,
        version,
        user,
        "editar-texto",
        requirePage(ref.pageCode()),
        () -> {
          long codPre = writes.copyQuestionForYear(capProyId, survey, yearText);
          writes.updateQuestionText(codPre, plain);
          return List.of(codeOf(ref));
        },
        false);
  }

  public AdminEditResult editHeader(
      String survey, int year, long codEnc, String text, int version, String user) {
    String plain = normalize(text);
    years.requireDraft(survey, year);
    String yearText = String.valueOf(year);
    long capProyId =
        lookup
            .capProyOfHeader(codEnc, yearText)
            .orElseThrow(() -> new ResourceNotFoundException("El encabezado no existe."));
    CapProyRef ref = requireCapProy(capProyId, survey, year);
    return apply(
        survey,
        year,
        version,
        user,
        "editar-encabezado",
        requirePage(ref.pageCode()),
        () -> {
          writes.updateHeader(codEnc, survey, yearText, plain);
          return List.of(codeOf(ref), "enc-" + codEnc);
        },
        false);
  }

  public AdminEditResult editHelp(
      String survey, int year, long codAyu, String text, int version, String user) {
    String plain = normalize(text);
    years.requireDraft(survey, year);
    long capProyId =
        lookup
            .capProyOfHelp(codAyu)
            .orElseThrow(() -> new ResourceNotFoundException("La ayuda no existe."));
    CapProyRef ref = requireCapProy(capProyId, survey, year);
    return apply(
        survey,
        year,
        version,
        user,
        "editar-ayuda",
        requirePage(ref.pageCode()),
        () -> {
          writes.updateHelp(codAyu, survey, String.valueOf(year), plain);
          return List.of(codeOf(ref), "ayu-" + codAyu);
        },
        false);
  }

  public AdminEditResult reorderPage(
      String survey, int year, String pageCode, List<Long> capProyIds, int version, String user) {
    SurveyPage page = requirePage(pageCode);
    years.requireDraft(survey, year);
    String yearText = String.valueOf(year);
    return apply(
        survey,
        year,
        version,
        user,
        "reordenar",
        page,
        () -> {
          List<PageRow> rows = questionnaire.pageRows(page, survey, year);
          Set<Long> current = new LinkedHashSet<>();
          rows.forEach(row -> current.add(row.row().entryId()));
          if (capProyIds.size() != current.size() || !current.equals(new HashSet<>(capProyIds))) {
            throw new InvalidEditException("La lista no coincide con las preguntas de la página.");
          }
          for (int position = 0; position < capProyIds.size(); position++) {
            writes.updateOrder(capProyIds.get(position), survey, yearText, position + 1);
          }
          Set<String> codes = new LinkedHashSet<>();
          rows.forEach(row -> codes.add(codeOf(row.row().entryId(), row.row().numeral())));
          return List.copyOf(codes);
        },
        true);
  }

  /**
   * Aplica un cambio bajo el candado global: comprueba borrador y versión, ejecuta las escrituras
   * FRM_* (que devuelven los códigos afectados para la auditoría) y guarda el archivo del año.
   */
  private AdminEditResult apply(
      String survey,
      int year,
      int version,
      String user,
      String operation,
      SurveyPage page,
      Supplier<List<String>> frmWrites,
      boolean warnAboutSkips) {
    locks.withLock(
        LOCK_KEY,
        () -> {
          try {
            years.requireDraft(survey, year);
            RuleSet rules = years.requireYear(survey, year);
            if (rules.version() != version) {
              throw new ConflictException(
                  "Otro usuario modificó el cuestionario. Recargue la página.");
            }
            transaction.executeWithoutResult(
                status -> {
                  List<String> codes = frmWrites.get();
                  rulesFiles.save(rules, version, user, operation, codes);
                });
          } catch (NoSuchElementException exception) {
            throw new ResourceNotFoundException("El registro que intenta editar no existe.");
          } finally {
            questionnaire.evict();
            effectiveRules.evict();
            tables.evict();
          }
          return null;
        });
    int newVersion = years.requireYear(survey, year).version();
    AdminPage refreshed = view.page(survey, year, page);
    List<String> warnings =
        warnAboutSkips ? skipsTargetingEarlier(refreshed, survey, year) : List.of();
    return new AdminEditResult(newVersion, refreshed, warnings);
  }

  private CapProyRef requireCapProy(long capProyId, String survey, int year) {
    return lookup
        .capProyOfYear(capProyId, survey, year)
        .orElseThrow(() -> new ResourceNotFoundException("La pregunta no existe en ese año."));
  }

  private static SurveyPage requirePage(String pageCode) {
    return SurveyPage.find(pageCode)
        .orElseThrow(() -> new ResourceNotFoundException("La página no existe."));
  }

  private static String codeOf(CapProyRef ref) {
    return codeOf(ref.capProyId(), ref.numeral());
  }

  private static String codeOf(long capProyId, String numeral) {
    return ColumnCodeResolver.fromNumeral(numeral).orElse("cap-" + capProyId);
  }

  /**
   * Texto plano estable: lo que se guarda es exactamente lo que luego se lee y se muestra. Se
   * repite la limpieza de {@link QuestionTextSanitizer} hasta que no cambie, para que una entidad
   * (por ejemplo la que decodifica a una etiqueta) no reaparezca como marcado al leerse de nuevo.
   */
  static String normalize(String text) {
    String current = text == null ? "" : text;
    for (int pass = 0; pass < MAX_NORMALIZATION_PASSES; pass++) {
      String cleaned =
          QuestionTextSanitizer.toPlainText(CONTROL_CHARACTERS.matcher(current).replaceAll(""));
      if (cleaned.equals(current)) {
        break;
      }
      current = cleaned;
    }
    if (current.isBlank()) {
      throw new InvalidEditException("El texto no puede quedar vacío.");
    }
    return current;
  }

  /** Reglas cuyo destino queda ahora antes de la pregunta que las dispara, dentro de la página. */
  private List<String> skipsTargetingEarlier(AdminPage page, String survey, int year) {
    List<String> order = new ArrayList<>();
    for (AdminEntry entry : page.entries()) {
      if (entry.columnCode() != null) {
        order.add(entry.columnCode());
      }
    }
    Set<String> flagged = new LinkedHashSet<>();
    for (SkipRule rule : effectiveRules.effectiveRules(survey, year).skips()) {
      if (rule.then() == null || rule.then().questions() == null) {
        continue;
      }
      Set<String> triggers = new HashSet<>();
      QuestionnaireAdminService.collect(rule.when(), triggers);
      for (String trigger : triggers) {
        for (String target : rule.then().questions()) {
          int triggerPosition = order.indexOf(trigger);
          int targetPosition = order.indexOf(target);
          if (triggerPosition >= 0 && targetPosition >= 0 && targetPosition < triggerPosition) {
            flagged.add(rule.id());
          }
        }
      }
    }
    return List.copyOf(flagged);
  }
}
