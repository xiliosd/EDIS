package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.FrmLookupRepository.CapProyRef;
import co.gov.dane.edi.admin.FrmLookupRepository.GroupOrder;
import co.gov.dane.edi.admin.FrmWriteRepository.NewQuestion;
import co.gov.dane.edi.admin.dto.AdminEditResult;
import co.gov.dane.edi.admin.dto.AdminEntry;
import co.gov.dane.edi.admin.dto.AdminPage;
import co.gov.dane.edi.admin.dto.NewQuestionRequest;
import co.gov.dane.edi.model.EntryKind;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.NewColumn;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.QuestionnaireService.PageRow;
import co.gov.dane.edi.service.SaveLocks;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.InvalidEditException;
import co.gov.dane.edi.service.error.ReferencedByRulesException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import co.gov.dane.edi.util.ColumnCodeResolver;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Edición de textos, encabezados, ayudas y orden de un año en borrador (copia al escribir).
 *
 * <p>Orden de cada cambio: candado global de administración ({@code SaveLocks.withAdminLock}); año
 * en borrador y versión vigente; y dentro de una sola transacción, las escrituras FRM_* y al final
 * el guardado del archivo del año (que sube su versión). Si el archivo no se puede guardar la
 * transacción se revierte, así la base y la versión no divergen por una falla de disco; solo un
 * fallo del COMMIT, muy poco probable, dejaría la versión subida sin cambio de datos, algo inocuo.
 * Las cachés se descartan siempre. La versión nueva solo se ve tras el COMMIT ({@link
 * RulesFileRepository#save}) y la respuesta (versión y página releída) se arma sin soltar el
 * candado, de modo que corresponde al estado que dejó este cambio y no a uno posterior.
 */
@Service
public class QuestionAdminService {

  private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
  private static final int MAX_STORED_BYTES = 4000;
  private static final Pattern NUMERAL = Pattern.compile("[0-9A-Za-z_.* ]{1,20}");

  private final YearCatalogService years;
  private final RulesFileRepository rulesFiles;
  private final FrmWriteRepository writes;
  private final FrmLookupRepository lookup;
  private final QuestionnaireAdminService view;
  private final QuestionnaireService questionnaire;
  private final EffectiveRulesService effectiveRules;
  private final AnswerTableResolver tables;
  private final AnswerColumnRepository answerColumns;
  private final ColumnScriptService scripts;
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
      AnswerColumnRepository answerColumns,
      ColumnScriptService scripts,
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
    this.answerColumns = answerColumns;
    this.scripts = scripts;
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
   * Agrega una pregunta propia al año en borrador. Su columna queda pendiente hasta que la persona
   * técnica ejecute el script de {@link #columnScript}; si la columna ya existe, queda activa.
   */
  public AdminEditResult addQuestion(
      String survey, int year, String pageCode, NewQuestionRequest request, String user) {
    SurveyPage page = requirePage(pageCode);
    years.requireDraft(survey, year);
    String plain = normalize(request.text());
    String numeral = validateNewQuestion(request);
    String code = request.columnCode().strip();
    NewColumn definition = definitionOf(request);
    return apply(
        survey,
        year,
        request.version(),
        user,
        "agregar-pregunta",
        page,
        () -> insertQuestion(survey, year, page, request, numeral, plain),
        false,
        rules -> {
          Map<String, NewColumn> columns = new LinkedHashMap<>(rules.newColumns());
          columns.put(code, definition);
          return rules.withNewColumns(columns);
        });
  }

  /** Quita la pregunta del año (solo su periodo); rechaza si alguna regla vigente la usa. */
  public AdminEditResult deactivateQuestion(
      String survey, int year, long capProyId, int version, String user) {
    years.requireDraft(survey, year);
    CapProyRef ref = requireCapProy(capProyId, survey, year);
    return apply(
        survey,
        year,
        version,
        user,
        "desactivar-pregunta",
        requirePage(ref.pageCode()),
        () -> {
          Optional<String> column = ColumnCodeResolver.fromNumeral(ref.numeral());
          if (column.isPresent()) {
            List<String> rules =
                QuestionnaireAdminService.rulesReferencing(
                    effectiveRules.effectiveRules(survey, year), column.get());
            if (!rules.isEmpty()) {
              throw new ReferencedByRulesException(rules);
            }
          }
          writes.deletePeriod(capProyId, survey, year);
          return List.of(codeOf(ref));
        },
        false,
        rules -> {
          Map<String, NewColumn> columns = new LinkedHashMap<>(rules.newColumns());
          ColumnCodeResolver.fromNumeral(ref.numeral()).ifPresent(columns::remove);
          return rules.withNewColumns(columns);
        });
  }

  /**
   * Script ALTER TABLE de la columna pendiente. Sin tipo explícito se deduce de la pregunta: las de
   * opciones usan el valor más largo de su grupo y las de texto 4000 caracteres.
   *
   * @throws ResourceNotFoundException si la pregunta no existe, no lleva columna o ya la tiene
   */
  public String columnScript(
      String survey, int year, long capProyId, String columnType, Integer columnLength) {
    RuleSet file = years.requireYear(survey, year);
    CapProyRef ref = requireCapProy(capProyId, survey, year);
    SurveyPage page = requirePage(ref.pageCode());
    PageRow row =
        questionnaire.pageRows(page, survey, year).stream()
            .filter(built -> built.row().entryId() == capProyId)
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("La pregunta no existe en ese año."));
    QuestionnaireEntry entry = row.entry();
    String column = entry.answer() == null ? null : entry.answer().columnCode();
    if (entry.kind() != EntryKind.ITEM) {
      throw new ResourceNotFoundException("La pregunta no tiene columna de respuesta.");
    }
    if (column == null) {
      throw new InvalidEditException(
          "columnCode", "El numeral de la pregunta no da un código de columna válido.");
    }
    String table = tables.tableFor(page.chapter(), survey, year);
    answerColumns.evict(table);
    if (answerColumns.findColumns(table).contains(column)) {
      throw new ResourceNotFoundException("La columna ya existe en la tabla.");
    }
    String type = columnType;
    Integer length = columnLength;
    if (type == null && length == null) {
      NewColumn stored = file.newColumns().get(column);
      if (stored != null) {
        type = stored.type();
        length = stored.length();
      } else {
        type = ColumnScriptService.VARCHAR2;
        length = defaultLength(entry.answer().inputType(), row.row().optionGroup());
      }
    }
    requireColumnDefinition(type, length);
    try {
      return scripts.alterTable(table, column, type, length);
    } catch (IllegalArgumentException exception) {
      throw new InvalidEditException(
          "columnCode", "No se puede generar el script para esa tabla o columna.");
    }
  }

  /** Lo elegido por la persona o, sin elección, el tipo deducido de la pregunta. */
  private NewColumn definitionOf(NewQuestionRequest request) {
    if (request.columnType() != null) {
      return new NewColumn(request.columnType().toUpperCase(Locale.ROOT), request.columnLength());
    }
    return new NewColumn(
        ColumnScriptService.VARCHAR2, defaultLength(request.inputType(), request.optionGroup()));
  }

  private int defaultLength(InputType inputType, Integer optionGroup) {
    if (inputType == InputType.TEXT) {
      return ColumnScriptService.MAX_VARCHAR2_LENGTH;
    }
    return optionGroup == null ? 1 : lookup.longestOptionValue(optionGroup);
  }

  private static void requireColumnDefinition(String type, Integer length) {
    String problem = ColumnScriptService.describeProblem(type, length);
    if (problem != null) {
      boolean knownType =
          ColumnScriptService.VARCHAR2.equalsIgnoreCase(type)
              || ColumnScriptService.NUMBER.equalsIgnoreCase(type);
      throw new InvalidEditException(knownType ? "columnLength" : "columnType", problem);
    }
  }

  /** Valida lo que no depende de la base y devuelve el numeral recortado. */
  private static String validateNewQuestion(NewQuestionRequest request) {
    String code = request.columnCode().strip();
    if (!ColumnScriptService.COLUMN.matcher(code).matches()) {
      throw new InvalidEditException(
          "columnCode", "El código de columna debe empezar con una letra mayúscula (2 a 30).");
    }
    String numeral = request.numeral().strip();
    if (!NUMERAL.matcher(numeral).matches()
        || !ColumnCodeResolver.fromNumeral(numeral).filter(code::equals).isPresent()) {
      throw new InvalidEditException(
          "numeral", "El numeral debe corresponder a la columna, por ejemplo C30A.");
    }
    InputType inputType = request.inputType();
    if (inputType == InputType.UNKNOWN) {
      throw new InvalidEditException("inputType", "Elija un tipo de respuesta.");
    }
    if (inputType == InputType.SINGLE_CHOICE && request.optionGroup() == null) {
      throw new InvalidEditException("optionGroup", "Elija el grupo de opciones.");
    }
    if (inputType == InputType.TEXT && request.optionGroup() != null) {
      throw new InvalidEditException(
          "optionGroup", "Las preguntas de texto no llevan grupo de opciones.");
    }
    if (request.columnType() != null || request.columnLength() != null) {
      requireColumnDefinition(request.columnType(), request.columnLength());
    }
    return numeral;
  }

  private List<String> insertQuestion(
      String survey,
      int year,
      SurveyPage page,
      NewQuestionRequest request,
      String numeral,
      String plain) {
    String code = request.columnCode().strip();
    for (SurveyPage sibling : SurveyPage.ordered()) {
      if (!sibling.chapter().equals(page.chapter())) {
        continue;
      }
      for (PageRow row : questionnaire.pageRows(sibling, survey, year)) {
        QuestionnaireEntry entry = row.entry();
        if (entry.answer() != null && code.equals(entry.answer().columnCode())) {
          throw new InvalidEditException(
              "columnCode", "Ya existe una pregunta con esa columna en el capítulo.");
        }
        if (sibling.equals(page) && numeral.equalsIgnoreCase(entry.numeral())) {
          throw new InvalidEditException("numeral", "Ese numeral ya existe en la página.");
        }
      }
    }
    if (request.optionGroup() != null && !lookup.optionGroupExists(request.optionGroup())) {
      throw new InvalidEditException("optionGroup", "El grupo de opciones no existe.");
    }
    List<Long> ids =
        questionnaire.pageRows(page, survey, year).stream()
            .map(row -> row.row().entryId())
            .distinct()
            .toList();
    Long anchor = request.afterCapProyId();
    if (anchor != null) {
      requireCapProy(anchor, survey, year);
      if (!ids.contains(anchor)) {
        throw new InvalidEditException(
            "afterCapProyId", "La pregunta de referencia está en otra página.");
      }
    }
    Map<Long, GroupOrder> orders = lookup.groupOrders(ids);
    int group = 1;
    int newOrder = 1;
    List<Long> following = new ArrayList<>();
    if (!ids.isEmpty()) {
      GroupOrder reference = orders.get(anchor == null ? ids.get(0) : anchor);
      group = reference.group();
      newOrder = anchor == null ? reference.order() : reference.order() + 1;
      int start = anchor == null ? 0 : ids.indexOf(anchor) + 1;
      for (int position = start; position < ids.size(); position++) {
        if (orders.get(ids.get(position)).group() == group) {
          following.add(ids.get(position));
        }
      }
    }
    // Solo se corren las siguientes lo necesario para dejar libre el nuevo ORDEN del mismo GRUPO.
    Map<Long, Integer> reorder = new LinkedHashMap<>();
    int previous = newOrder;
    for (Long id : following) {
      int current = orders.get(id).order();
      int target = current <= previous ? previous + 1 : current;
      if (target != current) {
        reorder.put(id, target);
      }
      previous = target;
    }
    long capProyId =
        writes.insertQuestion(
            new NewQuestion(
                survey,
                year,
                page.code(),
                numeral,
                plain,
                objectCodeOf(request.inputType()),
                request.optionGroup(),
                group,
                newOrder),
            reorder);
    List<Long> expected = new ArrayList<>(ids);
    expected.add(anchor == null ? 0 : ids.indexOf(anchor) + 1, capProyId);
    List<Long> reread =
        questionnaire.pageRows(page, survey, year).stream()
            .map(row -> row.row().entryId())
            .distinct()
            .toList();
    if (!reread.equals(expected)) {
      throw new IllegalStateException("La pregunta nueva no quedó en la posición solicitada.");
    }
    return List.of(code);
  }

  private static int objectCodeOf(InputType inputType) {
    return switch (inputType) {
      case SINGLE_CHOICE -> 1;
      case CHECKBOX -> 2;
      case TEXT -> 5;
      case UNKNOWN -> throw new IllegalArgumentException("Tipo de respuesta desconocido");
    };
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
    return apply(
        survey, year, version, user, operation, page, frmWrites, warnAboutSkips, rules -> rules);
  }

  /** {@code fileChange} ajusta las reglas del año que se guardan junto con las escrituras FRM_*. */
  private AdminEditResult apply(
      String survey,
      int year,
      int version,
      String user,
      String operation,
      SurveyPage page,
      Supplier<List<String>> frmWrites,
      boolean warnAboutSkips,
      UnaryOperator<RuleSet> fileChange) {
    // La página de la respuesta se arma dentro del candado: otra edición no puede colarse entre el
    // COMMIT y la lectura, así la versión devuelta corresponde exactamente a la página devuelta.
    return locks.withAdminLock(
        () -> {
          RuleSet saved;
          try {
            years.requireDraft(survey, year);
            RuleSet rules = years.requireYear(survey, year);
            if (rules.version() != version) {
              throw new ConflictException(
                  "Otro usuario modificó el cuestionario. Recargue la página.");
            }
            saved =
                transaction.execute(
                    status -> {
                      List<String> codes = frmWrites.get();
                      return rulesFiles.save(
                          fileChange.apply(rules), version, user, operation, codes);
                    });
          } catch (NoSuchElementException exception) {
            throw new ResourceNotFoundException("El registro que intenta editar no existe.");
          } finally {
            questionnaire.evict();
            effectiveRules.evict();
            tables.evict();
          }
          AdminPage refreshed = view.page(survey, year, page);
          List<String> warnings =
              warnAboutSkips ? skipsTargetingEarlier(refreshed, survey, year) : List.of();
          return new AdminEditResult(saved.version(), refreshed, warnings);
        });
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
