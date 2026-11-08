package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.admin.AnswerTableResolver;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Tablas de capítulo de un año para los reportes nominales, con la comprobación de B07 y H06. */
@Component
public class ReportChapterTables {

  private final AnswerTableResolver tables;
  private final SurveyProperties survey;
  private final CoverageService coverageService;

  public ReportChapterTables(
      AnswerTableResolver tables, SurveyProperties survey, CoverageService coverageService) {
    this.tables = tables;
    this.survey = survey;
    this.coverageService = coverageService;
  }

  /** Los siete capítulos B a H. */
  Map<Chapter, String> allChapters(int year) {
    return resolve(year, Chapter.values());
  }

  /** Solo B y H, suficientes para el estado de cada funcionario. */
  Map<Chapter, String> statusChapters(int year) {
    return resolve(year, Chapter.B, Chapter.H);
  }

  private Map<Chapter, String> resolve(int year, Chapter... chapters) {
    Map<Chapter, String> resolved = new EnumMap<>(Chapter.class);
    for (Chapter chapter : chapters) {
      resolved.put(chapter, tables.tableFor(chapter, survey.code(), year));
    }
    // Sin B07 o H06 el estado no se puede calcular: 422, igual que la cobertura.
    coverageService.requireCompletionQuestions(
        year,
        tables.tableFor(Chapter.B, survey.code(), year),
        tables.tableFor(Chapter.H, survey.code(), year));
    return resolved;
  }
}
