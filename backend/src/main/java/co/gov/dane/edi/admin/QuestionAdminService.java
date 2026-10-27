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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
  private static final int MAX_STORED_BYTES = 4000;

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
          List<Long> current = rows.stream().map(row -> row.row().entryId()).distinct().toList();
          if (capProyIds.size() != current.size()
              || !new HashSet<>(current).equals(new HashSet<>(capProyIds))) {
            throw new InvalidEditException("La lista no coincide con las preguntas de la página.");
          }
          Map<Long, Integer> groups = lookup.groupNumbers(current);
          // La lectura ordena por GRUPO y luego ORDEN: solo se puede permutar dentro de cada
          // bloque.
          for (int position = 0; position < current.size(); position++) {
            if (!groups.get(current.get(position)).equals(groups.get(capProyIds.get(position)))) {
              throw new InvalidEditException("No se puede mover una pregunta a otro grupo.");
            }
          }
          Map<Integer, Integer> nextOrder = new HashMap<>();
          for (Long capProyId : capProyIds) {
            int order = nextOrder.merge(groups.get(capProyId), 1, Integer::sum);
            writes.updateOrder(capProyId, survey, yearText, order);
          }
          List<Long> reread =
              questionnaire.pageRows(page, survey, year).stream()
                  .map(row -> row.row().entryId())
                  .distinct()
                  .toList();
          if (!reread.equals(capProyIds)) {
            throw new IllegalStateException("El orden guardado no coincide con el solicitado.");
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
   * Texto plano a su forma guardada: sin caracteres de control, recortado y con solo {@code &},
   * {@code <} y {@code >} escapados. La lectura (quitar etiquetas y luego decodificar entidades) lo
   * devuelve tal cual lo escribió la persona.
   */
  static String normalize(String text) {
    String stripped = CONTROL_CHARACTERS.matcher(text == null ? "" : text).replaceAll("").strip();
    if (stripped.isEmpty()) {
      throw new InvalidEditException("text", "El texto no puede quedar vacío.");
    }
    String escaped = stripped.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    if (escaped.getBytes(StandardCharsets.UTF_8).length > MAX_STORED_BYTES) {
      throw new InvalidEditException("text", "El texto es demasiado largo.");
    }
    return escaped;
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
