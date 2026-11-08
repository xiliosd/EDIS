package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.admin.AnswerTableResolver;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.report.CoverageRepository.CoverageRow;
import co.gov.dane.edi.operation.report.dto.CoverageReport;
import co.gov.dane.edi.operation.report.dto.CoverageTotals;
import co.gov.dane.edi.operation.report.dto.DepartmentCoverage;
import co.gov.dane.edi.operation.report.dto.EntityCoverage;
import co.gov.dane.edi.operation.report.dto.LevelCoverage;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Cobertura por departamento y entidad con el criterio único de estado. Una consulta agrupada por
 * petición (nunca una por departamento); entidades, departamentos y nación se suman en Java.
 */
@Service
public class CoverageService {

  private static final int WEB_CHANNEL = 1;
  private static final List<Integer> LEVELS = List.of(1, 2, 3);
  private static final String COMPLETE_FROM = "B07";
  private static final String COMPLETE_TO = "H06";

  /** Años cuyos capítulos B y H ya se comprobaron: solo se guarda lo afirmativo. */
  private final Set<Integer> verifiedYears = ConcurrentHashMap.newKeySet();

  private final CoverageRepository repository;
  private final AnswerTableResolver tables;
  private final SurveyProperties survey;
  private final Clock clock;

  public CoverageService(
      CoverageRepository repository,
      AnswerTableResolver tables,
      SurveyProperties survey,
      Clock clock) {
    this.repository = repository;
    this.tables = tables;
    this.survey = survey;
    this.clock = clock;
  }

  public CoverageReport coverage(
      OperationalAccess access, ReportSurvey reportSurvey, int year, Integer departmentCode) {
    // Nacional u operativo sin departamento pedido: sin filtro (el catálogo puede no tenerlos
    // todos).
    List<Integer> departments =
        access.allDepartments() && departmentCode == null
            ? null
            : access.departmentCodesOrAll(departmentCode);
    String chapterB = tables.tableFor(Chapter.B, survey.code(), year);
    String chapterH = tables.tableFor(Chapter.H, survey.code(), year);
    requireCompletionQuestions(year, chapterB, chapterH);
    List<CoverageRow> rows =
        repository.findRows(chapterB, chapterH, year, reportSurvey, departments);
    return buildReport(reportSurvey, year, rows);
  }

  private void requireCompletionQuestions(int year, String chapterB, String chapterH) {
    if (verifiedYears.contains(year)) {
      return;
    }
    if (!repository.hasColumn(chapterB, COMPLETE_FROM)
        || !repository.hasColumn(chapterH, COMPLETE_TO)) {
      throw new ReportUnavailableException(year);
    }
    verifiedYears.add(year);
  }

  private CoverageReport buildReport(ReportSurvey reportSurvey, int year, List<CoverageRow> rows) {
    Map<Integer, List<CoverageRow>> byDepartment = new LinkedHashMap<>();
    rows.forEach(
        row ->
            byDepartment.computeIfAbsent(row.departmentCode(), key -> new ArrayList<>()).add(row));
    List<DepartmentCoverage> departments =
        byDepartment.values().stream()
            .map(this::departmentCoverage)
            .sorted(
                Comparator.comparing(DepartmentCoverage::name)
                    .thenComparingInt(DepartmentCoverage::code))
            .toList();
    CoverageTotals national =
        departments.stream()
            .map(DepartmentCoverage::totals)
            .reduce(CoverageTotals::plus)
            .orElse(CoverageTotals.of(0, 0, 0, 0));
    Set<Long> distinctEntities = new HashSet<>();
    departments.forEach(
        department -> department.entities().forEach(entity -> distinctEntities.add(entity.code())));
    return new CoverageReport(
        reportSurvey,
        year,
        OffsetDateTime.now(clock),
        departments,
        departments.size(),
        distinctEntities.size(),
        national,
        levelTotals(rows));
  }

  private DepartmentCoverage departmentCoverage(List<CoverageRow> departmentRows) {
    Map<Long, List<CoverageRow>> byEntity = new LinkedHashMap<>();
    departmentRows.forEach(
        row -> byEntity.computeIfAbsent(row.entityCode(), key -> new ArrayList<>()).add(row));
    List<EntityCoverage> entities =
        byEntity.values().stream()
            .map(CoverageService::entityCoverage)
            .sorted(Comparator.comparingLong(EntityCoverage::code))
            .toList();
    CoverageTotals totals =
        entities.stream()
            .map(EntityCoverage::totals)
            .reduce(CoverageTotals::plus)
            .orElse(CoverageTotals.of(0, 0, 0, 0));
    CoverageRow first = departmentRows.get(0);
    return new DepartmentCoverage(first.departmentCode(), first.departmentName(), entities, totals);
  }

  private static EntityCoverage entityCoverage(List<CoverageRow> entityRows) {
    CoverageRow first = entityRows.get(0);
    String name =
        first.entityName() == null || first.entityName().isBlank()
            ? "Entidad " + first.entityCode()
            : first.entityName();
    return new EntityCoverage(
        first.entityCode(), name, totalsOf(entityRows), levelTotals(entityRows));
  }

  /** Niveles 1 a 3 de la carga web; el nivel sin funcionarios queda en cero. */
  private static List<LevelCoverage> levelTotals(List<CoverageRow> rows) {
    return LEVELS.stream()
        .map(
            level ->
                new LevelCoverage(
                    level,
                    totalsOf(
                        rows.stream()
                            .filter(row -> Integer.valueOf(WEB_CHANNEL).equals(row.channel()))
                            .filter(row -> level.equals(row.level()))
                            .toList())))
        .toList();
  }

  private static CoverageTotals totalsOf(List<CoverageRow> rows) {
    long sample = 0;
    long novelties = 0;
    long complete = 0;
    long incomplete = 0;
    for (CoverageRow row : rows) {
      sample += row.total();
      switch (row.status()) {
        case COMPLETE -> complete += row.total();
        case NOVELTY -> novelties += row.total();
        case INCOMPLETE -> incomplete += row.total();
        default -> {
          // Sin diligenciar se deduce: efectiva - completas - incompletas.
        }
      }
    }
    return CoverageTotals.of(sample, novelties, complete, incomplete);
  }
}
