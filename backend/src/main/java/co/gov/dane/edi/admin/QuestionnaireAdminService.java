package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.dto.AdminEntry;
import co.gov.dane.edi.admin.dto.AdminHeader;
import co.gov.dane.edi.admin.dto.AdminHelp;
import co.gov.dane.edi.admin.dto.AdminPage;
import co.gov.dane.edi.admin.dto.AdminQuestionnaireView;
import co.gov.dane.edi.model.AnswerSpec;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.QuestionnaireRepository;
import co.gov.dane.edi.rules.Condition;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.QuestionnaireService.PageRow;
import co.gov.dane.edi.util.QuestionTextSanitizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Arma el árbol de lectura del cuestionario de cualquier encuesta y año. */
@Service
public class QuestionnaireAdminService {

  private final YearCatalogService years;
  private final QuestionnaireService questionnaire;
  private final EffectiveRulesService effectiveRules;
  private final AnswerTableResolver tables;
  private final QuestionnaireRepository repository;

  public QuestionnaireAdminService(
      YearCatalogService years,
      QuestionnaireService questionnaire,
      EffectiveRulesService effectiveRules,
      AnswerTableResolver tables,
      QuestionnaireRepository repository) {
    this.years = years;
    this.questionnaire = questionnaire;
    this.effectiveRules = effectiveRules;
    this.tables = tables;
    this.repository = repository;
  }

  public AdminQuestionnaireView view(String survey, int year) {
    RuleSet file = years.requireYear(survey, year);
    RuleSet rules = effectiveRules.effectiveRules(survey, year);
    Map<String, String> chapterTables = new LinkedHashMap<>();
    for (Chapter chapter : Chapter.values()) {
      chapterTables.put(chapter.code(), tables.tableFor(chapter, survey, year));
    }
    List<AdminPage> pages = new ArrayList<>();
    for (SurveyPage page : SurveyPage.ordered()) {
      pages.add(toAdminPage(page, survey, year, rules, chapterTables.get(page.chapter().code())));
    }
    return new AdminQuestionnaireView(
        survey, year, file.effectiveState(), file.version(), chapterTables, pages);
  }

  /** Una sola página, con los datos de la base y las reglas vigentes (tras una edición). */
  public AdminPage page(String survey, int year, SurveyPage page) {
    years.requireYear(survey, year);
    RuleSet rules = effectiveRules.effectiveRules(survey, year);
    return toAdminPage(page, survey, year, rules, tables.tableFor(page.chapter(), survey, year));
  }

  private AdminPage toAdminPage(
      SurveyPage page, String survey, int year, RuleSet rules, String tableName) {
    return new AdminPage(
        page.code(), page.chapter().code(), tableName, entriesOf(page, survey, year, rules));
  }

  private List<AdminEntry> entriesOf(SurveyPage page, String survey, int year, RuleSet rules) {
    List<PageRow> rows = questionnaire.pageRows(page, survey, year);
    List<Long> ids = rows.stream().map(built -> built.row().entryId()).toList();
    Map<Long, List<AdminHeader>> headers = repository.findAdminHeaders(ids, String.valueOf(year));
    Map<Long, AdminHelp> help = repository.findHelp(ids);
    return rows.stream().map(built -> toAdminEntry(built, headers, help, rules)).toList();
  }

  private static AdminEntry toAdminEntry(
      PageRow built,
      Map<Long, List<AdminHeader>> headers,
      Map<Long, AdminHelp> help,
      RuleSet rules) {
    QuestionnaireEntry entry = built.entry();
    AnswerSpec answer = entry.answer();
    long id = entry.id();
    List<AdminHeader> entryHeaders =
        headers.getOrDefault(id, List.of()).stream()
            .map(
                h ->
                    new AdminHeader(
                        h.codEnc(), h.kind(), QuestionTextSanitizer.toPlainText(h.text())))
            .toList();
    AdminHelp entryHelp = help.get(id);
    AdminEntry admin =
        new AdminEntry(
            id,
            entry.numeral(),
            answer == null ? null : answer.columnCode(),
            entry.kind().name(),
            answer == null ? null : answer.inputType().name(),
            entry.text(),
            answer == null ? null : answer.columnStatus(),
            built.row().optionGroup(),
            entryHeaders,
            entryHelp == null
                ? null
                : new AdminHelp(
                    entryHelp.codAyu(), QuestionTextSanitizer.toPlainText(entryHelp.text())),
            List.of());
    return admin.withReferencedByRules(referencingRules(admin, rules));
  }

  private static List<String> referencingRules(AdminEntry entry, RuleSet rules) {
    if (entry.columnCode() == null) {
      return List.of();
    }
    return rules.skips().stream()
        .filter(rule -> mentions(rule, entry.columnCode()))
        .map(SkipRule::id)
        .toList();
  }

  private static boolean mentions(SkipRule rule, String column) {
    Set<String> columns = new HashSet<>();
    collect(rule.when(), columns);
    if (rule.then() != null && rule.then().questions() != null) {
      columns.addAll(rule.then().questions());
    }
    return columns.contains(column);
  }

  static void collect(Condition condition, Set<String> columns) {
    if (condition == null) {
      return;
    }
    if (condition.question() != null) {
      columns.add(condition.question());
    }
    if (condition.all() != null) {
      columns.addAll(condition.all());
    }
    if (condition.any() != null) {
      columns.addAll(condition.any());
    }
    if (condition.and() != null) {
      condition.and().forEach(inner -> collect(inner, columns));
    }
    if (condition.or() != null) {
      condition.or().forEach(inner -> collect(inner, columns));
    }
  }
}
