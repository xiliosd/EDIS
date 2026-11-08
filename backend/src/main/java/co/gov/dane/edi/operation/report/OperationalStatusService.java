package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.admin.AnswerTableResolver;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.report.dto.PersonStatusPage;
import co.gov.dane.edi.operation.report.dto.PersonStatusRow;
import co.gov.dane.edi.operation.report.dto.StatusFilter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Listado nominal "Estado de mi operativo". El departamento es obligatorio y debe estar en el
 * alcance del usuario (403 si no). Maneja datos personales: no registra nada en logs.
 */
@Service
public class OperationalStatusService {

  private final OperationalStatusRepository repository;
  private final CoverageService coverageService;
  private final AnswerTableResolver tables;
  private final SurveyProperties survey;

  public OperationalStatusService(
      OperationalStatusRepository repository,
      CoverageService coverageService,
      AnswerTableResolver tables,
      SurveyProperties survey) {
    this.repository = repository;
    this.coverageService = coverageService;
    this.tables = tables;
    this.survey = survey;
  }

  public PersonStatusPage page(
      OperationalAccess access,
      ReportSurvey reportSurvey,
      int year,
      int departmentCode,
      Long entityCode,
      StatusFilter statusFilter,
      int page,
      int size) {
    access.requireDepartment(departmentCode);
    Map<Chapter, String> chapterTables = chapterTables(year);
    List<PersonStatusRow> rows =
        repository.findRows(
            chapterTables,
            year,
            reportSurvey,
            departmentCode,
            entityCode,
            statusFilter,
            (long) page * size,
            size);
    long total =
        repository.count(
            chapterTables, year, reportSurvey, departmentCode, entityCode, statusFilter);
    return new PersonStatusPage(rows, page, size, total);
  }

  /** Todas las filas del filtro, para el Excel. */
  public List<PersonStatusRow> all(
      OperationalAccess access,
      ReportSurvey reportSurvey,
      int year,
      int departmentCode,
      Long entityCode,
      StatusFilter statusFilter) {
    access.requireDepartment(departmentCode);
    return repository.findRows(
        chapterTables(year), year, reportSurvey, departmentCode, entityCode, statusFilter, 0, null);
  }

  private Map<Chapter, String> chapterTables(int year) {
    Map<Chapter, String> chapterTables = new EnumMap<>(Chapter.class);
    for (Chapter chapter : Chapter.values()) {
      chapterTables.put(chapter, tables.tableFor(chapter, survey.code(), year));
    }
    // Sin B07 o H06 el estado no se puede calcular: 422, igual que la cobertura.
    coverageService.requireCompletionQuestions(
        year, chapterTables.get(Chapter.B), chapterTables.get(Chapter.H));
    return chapterTables;
  }
}
