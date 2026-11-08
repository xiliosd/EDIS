package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.report.dto.NoveltyReportRow;
import co.gov.dane.edi.operation.report.dto.PerformanceRow;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Reportes nominales de novedades y de rendimientos. Sin departamento pedido: todo el país para
 * quien lo ve, o los departamentos asignados al territorial; un departamento ajeno es 403. Manejan
 * datos personales: no registran nada en logs.
 */
@Service
public class NominalReportService {

  private final NoveltyReportRepository noveltyRepository;
  private final PerformanceReportRepository performanceRepository;
  private final ReportChapterTables chapterTables;

  public NominalReportService(
      NoveltyReportRepository noveltyRepository,
      PerformanceReportRepository performanceRepository,
      ReportChapterTables chapterTables) {
    this.noveltyRepository = noveltyRepository;
    this.performanceRepository = performanceRepository;
    this.chapterTables = chapterTables;
  }

  public List<NoveltyReportRow> novelties(
      OperationalAccess access, ReportSurvey reportSurvey, int year, Integer departmentCode) {
    List<Integer> departments = access.departmentFilter(departmentCode);
    return noveltyRepository.findRows(
        chapterTables.statusChapters(year), year, reportSurvey, departments);
  }

  public List<PerformanceRow> performance(
      OperationalAccess access, ReportSurvey reportSurvey, int year, Integer departmentCode) {
    List<Integer> departments = access.departmentFilter(departmentCode);
    return performanceRepository.findRows(
        chapterTables.allChapters(year), year, reportSurvey, departments);
  }
}
