package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.report.dto.PersonStatusPage;
import co.gov.dane.edi.operation.report.dto.PersonStatusRow;
import co.gov.dane.edi.operation.report.dto.StatusFilter;
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
  private final ReportChapterTables chapterTables;

  public OperationalStatusService(
      OperationalStatusRepository repository, ReportChapterTables chapterTables) {
    this.repository = repository;
    this.chapterTables = chapterTables;
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
    Map<Chapter, String> tablesByChapter = chapterTables.allChapters(year);
    List<PersonStatusRow> rows =
        repository.findRows(
            tablesByChapter,
            year,
            reportSurvey,
            departmentCode,
            entityCode,
            statusFilter,
            (long) page * size,
            size);
    long total =
        repository.count(
            tablesByChapter, year, reportSurvey, departmentCode, entityCode, statusFilter);
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
        chapterTables.allChapters(year),
        year,
        reportSurvey,
        departmentCode,
        entityCode,
        statusFilter,
        0,
        null);
  }
}
