package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.dto.AdminEntry;
import co.gov.dane.edi.admin.dto.AdminPage;
import co.gov.dane.edi.admin.dto.AdminQuestionnaireView;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.rules.Condition;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.service.QuestionnaireService;
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

  public QuestionnaireAdminService(
      YearCatalogService years,
      QuestionnaireService questionnaire,
      EffectiveRulesService effectiveRules,
      AnswerTableResolver tables) {
    this.years = years;
    this.questionnaire = questionnaire;
    this.effectiveRules = effectiveRules;
    this.tables = tables;
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
      List<AdminEntry> entries =
          questionnaire.adminEntries(page, survey, year).stream()
              .map(entry -> entry.withReferencedByRules(referencingRules(entry, rules)))
              .toList();
      pages.add(
          new AdminPage(
              page.code(),
              page.chapter().code(),
              chapterTables.get(page.chapter().code()),
              entries));
    }
    return new AdminQuestionnaireView(
        survey, year, file.effectiveState(), file.version(), chapterTables, pages);
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

  private static void collect(Condition condition, Set<String> columns) {
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
