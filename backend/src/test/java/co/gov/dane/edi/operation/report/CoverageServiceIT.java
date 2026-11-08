package co.gov.dane.edi.operation.report;

import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_COMPLETE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_INACTIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_INCOMPLETE;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_ANTIOQUIA;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_ATLANTICO;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_VALLE;
import static co.gov.dane.edi.support.OperationalSampleFixture.ENTITY_AT_SIGN;
import static co.gov.dane.edi.support.OperationalSampleFixture.ENTITY_EDID;
import static co.gov.dane.edi.support.OperationalSampleFixture.ENTITY_MIXED;
import static co.gov.dane.edi.support.OperationalSampleFixture.ENTITY_NO_CATALOG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.gov.dane.edi.admin.AnswerTableResolver;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.Department;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.report.dto.CoverageReport;
import co.gov.dane.edi.operation.report.dto.CoverageTotals;
import co.gov.dane.edi.operation.report.dto.DepartmentCoverage;
import co.gov.dane.edi.operation.report.dto.EntityCoverage;
import co.gov.dane.edi.operation.report.dto.LevelCoverage;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.support.OperationalSampleFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/**
 * Cobertura sobre la muestra ficticia. data.sql también está cargado (departamentos 11 y 25): las
 * cifras nacionales se piden con un alcance limitado a los departamentos del fixture.
 */
class CoverageServiceIT extends OracleIntegrationTest {

  private static final String H_WITHOUT_H06 = "EDIN_TEST_H_SIN_H06";

  @Autowired private CoverageService service;
  @Autowired private CoverageRepository repository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private NamedParameterJdbcTemplate namedJdbc;
  @Autowired private SurveyProperties survey;
  @Autowired private Clock clock;

  private OperationalSampleFixture fixture;
  private OperationalAccess fixtureScope;

  @BeforeEach
  void loadFixture() {
    fixture = new OperationalSampleFixture(jdbc);
    fixture.load(survey.year());
    fixtureScope =
        new OperationalAccess(
            false,
            List.of(
                new Department(DEPARTMENT_ANTIOQUIA, "A"),
                new Department(DEPARTMENT_ATLANTICO, "B"),
                new Department(DEPARTMENT_VALLE, "C")),
            true,
            false);
  }

  @AfterEach
  void clearFixture() {
    fixture.clear(survey.year());
    dropQuietly(H_WITHOUT_H06);
  }

  private void dropQuietly(String table) {
    try {
      jdbc.execute("DROP TABLE " + table + " PURGE");
    } catch (RuntimeException ignored) {
      // No existía.
    }
  }

  private CoverageReport report(ReportSurvey reportSurvey) {
    return service.coverage(fixtureScope, reportSurvey, survey.year(), null);
  }

  private static EntityCoverage entity(DepartmentCoverage department, long code) {
    return department.entities().stream()
        .filter(candidate -> candidate.code() == code)
        .findFirst()
        .orElseThrow();
  }

  private static DepartmentCoverage department(CoverageReport report, int code) {
    return report.departments().stream()
        .filter(candidate -> candidate.code() == code)
        .findFirst()
        .orElseThrow();
  }

  private static void assertTotals(
      CoverageTotals totals, long sample, long novelties, long complete, long incomplete) {
    assertThat(totals).isEqualTo(CoverageTotals.of(sample, novelties, complete, incomplete));
  }

  @Test
  void mixedEntityAppearsOnceAndAddsWebAndDmc() {
    DepartmentCoverage antioquia = department(report(ReportSurvey.GENERAL), 5);

    assertThat(antioquia.entities()).extracting(EntityCoverage::code).containsExactly(900001L);
    EntityCoverage mixed = entity(antioquia, ENTITY_MIXED);
    assertThat(mixed.name()).isEqualTo("Entidad Ficticia Mixta");
    // Web 1,2,3,4,5,6,13 y DMC 7,12; el inactivo (09) no cuenta.
    assertTotals(mixed.totals(), 9, 2, 2, 2);
    assertThat(mixed.totals().effectiveSample()).isEqualTo(7);
    assertThat(mixed.totals().notStarted()).isEqualTo(3);
    assertThat(mixed.totals().completePct()).isEqualByComparingTo("28.57");
    assertThat(mixed.totals().notStartedPct()).isEqualByComparingTo("42.86");
  }

  @Test
  void completeWithNoveltyCountsAsCompleteNotAsNovelty() {
    EntityCoverage mixed = entity(department(report(ReportSurvey.GENERAL), 5), ENTITY_MIXED);

    // Casos 5 y 6 son novedad; el 2 (completo con novedad) va en completas.
    assertThat(mixed.totals().novelties()).isEqualTo(2);
    assertThat(mixed.totals().complete()).isEqualTo(2);
  }

  @Test
  void webLevelsOnlyCountWebAndKeepStatusRule() {
    EntityCoverage mixed = entity(department(report(ReportSurvey.GENERAL), 5), ENTITY_MIXED);

    List<LevelCoverage> levels = mixed.webLevels();
    assertThat(levels).extracting(LevelCoverage::level).containsExactly(1, 2, 3);
    assertTotals(levels.get(0).totals(), 3, 0, 1, 0);
    assertTotals(levels.get(1).totals(), 2, 1, 1, 0);
    assertTotals(levels.get(2).totals(), 2, 1, 0, 1);
  }

  @Test
  void ediExcludesEdidAndRowsWithoutEnc() {
    CoverageReport report = report(ReportSurvey.EDI);

    assertThat(report.departments()).extracting(DepartmentCoverage::code).containsExactly(5, 8, 76);
    // El caso 4 (ENC nulo) sale; en Atlántico solo queda la entidad EDI.
    assertTotals(entity(department(report, 5), ENTITY_MIXED).totals(), 8, 2, 2, 2);
    assertThat(department(report, 8).entities())
        .extracting(EntityCoverage::code)
        .containsExactly((long) ENTITY_AT_SIGN);
    assertTotals(report.national(), 11, 2, 3, 3);
  }

  @Test
  void edidOnlyCountsEdidRows() {
    CoverageReport report = report(ReportSurvey.EDID);

    assertThat(report.departments()).extracting(DepartmentCoverage::code).containsExactly(8);
    assertThat(report.departments().get(0).entities())
        .extracting(EntityCoverage::code)
        .containsExactly((long) ENTITY_EDID);
    assertTotals(report.national(), 1, 0, 1, 0);
    assertThat(report.entityCount()).isEqualTo(1);
  }

  @Test
  void nationalConsolidatesFixtureAndCountsEntitiesNotEntityChannelPairs() {
    CoverageReport report = report(ReportSurvey.GENERAL);

    assertThat(report.survey()).isEqualTo(ReportSurvey.GENERAL);
    assertThat(report.year()).isEqualTo(survey.year());
    assertThat(report.generatedAt()).isNotNull();
    assertThat(report.departmentCount()).isEqualTo(3);
    // 900001 tiene web y DMC pero es una sola entidad.
    assertThat(report.entityCount()).isEqualTo(4);
    assertTotals(report.national(), 13, 2, 4, 3);
    assertThat(report.national().effectiveSample()).isEqualTo(11);
    assertThat(report.national().notStarted()).isEqualTo(4);
    assertThat(report.nationalWebLevels())
        .extracting(LevelCoverage::level)
        .containsExactly(1, 2, 3);
    // Web: nivel 1 = 1,4,13,10; nivel 2 = 2,5,11,8,14; nivel 3 = 3,6.
    assertTotals(report.nationalWebLevels().get(0).totals(), 4, 0, 1, 1);
    assertTotals(report.nationalWebLevels().get(1).totals(), 5, 1, 3, 0);
    assertTotals(report.nationalWebLevels().get(2).totals(), 2, 1, 0, 1);
  }

  @Test
  void departmentsAreOrderedByNameAndEntitiesByCode() {
    CoverageReport report = report(ReportSurvey.GENERAL);

    assertThat(report.departments())
        .extracting(DepartmentCoverage::name)
        .containsExactly(
            "DEPARTMENT_ANTIOQUIA (ficticio)",
            "DEPARTMENT_ATLANTICO (ficticio)",
            "DEPARTMENT_VALLE (ficticio)");
    assertThat(department(report, 8).entities())
        .extracting(EntityCoverage::code)
        .containsExactly((long) ENTITY_EDID, (long) ENTITY_AT_SIGN);
  }

  @Test
  void inactiveFunctionaryIsExcluded() {
    jdbc.update("UPDATE EDIN_MUESTRA SET ATV = 1 WHERE C_C = ?", CASE_INACTIVE);
    long withInactiveActivated =
        entity(department(report(ReportSurvey.GENERAL), 5), ENTITY_MIXED).totals().sample();
    jdbc.update("UPDATE EDIN_MUESTRA SET ATV = 0 WHERE C_C = ?", CASE_INACTIVE);
    long withInactive =
        entity(department(report(ReportSurvey.GENERAL), 5), ENTITY_MIXED).totals().sample();

    assertThat(withInactiveActivated).isEqualTo(10);
    assertThat(withInactive).isEqualTo(9);
  }

  @Test
  void entityWithoutCatalogIsPresentWithGeneratedName() {
    EntityCoverage withoutCatalog =
        entity(department(report(ReportSurvey.GENERAL), 76), ENTITY_NO_CATALOG);

    assertThat(withoutCatalog.name()).isEqualTo("Entidad 900099");
    assertTotals(withoutCatalog.totals(), 2, 0, 0, 1);
  }

  @Test
  void requestedDepartmentFiltersTheReport() {
    CoverageReport report = service.coverage(fixtureScope, ReportSurvey.GENERAL, survey.year(), 8);

    assertThat(report.departments()).extracting(DepartmentCoverage::code).containsExactly(8);
    assertThat(report.departmentCount()).isEqualTo(1);
    assertTotals(report.national(), 2, 0, 2, 0);
  }

  @Test
  void nationalAccessWithoutRequestedDepartmentDoesNotFilterByDepartment() {
    OperationalAccess everything = new OperationalAccess(true, List.of(), false, true);

    CoverageReport report = service.coverage(everything, ReportSurvey.GENERAL, survey.year(), null);

    // Sin catálogo en el acceso no hay IN vacío: se ven los del fixture y los de data.sql.
    assertThat(report.departments())
        .extracting(DepartmentCoverage::code)
        .contains(5, 8, 76, 11, 25);
  }

  @Test
  void yearWithoutSampleGivesEmptyReportWithZeroTotals() {
    CoverageReport report = service.coverage(fixtureScope, ReportSurvey.GENERAL, 2019, null);

    assertThat(report.departments()).isEmpty();
    assertThat(report.departmentCount()).isZero();
    assertThat(report.entityCount()).isZero();
    assertTotals(report.national(), 0, 0, 0, 0);
    assertThat(report.nationalWebLevels()).hasSize(3);
    assertThat(report.nationalWebLevels())
        .allSatisfy(level -> assertTotals(level.totals(), 0, 0, 0, 0));
  }

  @Test
  void territorialWithForeignDepartmentIsForbidden() {
    OperationalAccess territorial =
        new OperationalAccess(false, List.of(new Department(5, "A")), true, false);

    assertThatThrownBy(() -> service.coverage(territorial, ReportSurvey.GENERAL, survey.year(), 8))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void territorialWithoutDepartmentsIsForbidden() {
    OperationalAccess nobody = new OperationalAccess(false, List.of(), true, false);

    assertThatThrownBy(() -> service.coverage(nobody, ReportSurvey.GENERAL, survey.year(), null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void repeatedChapterRowsDoNotInflateSampleOrComplete() {
    CoverageTotals before =
        entity(department(report(ReportSurvey.GENERAL), 5), ENTITY_MIXED).totals();
    jdbc.update(
        "INSERT INTO EDIN_20_CAP_B_UBICA (C_C, ESTADO, TC, B07) VALUES (?, 1, 1, '1')",
        CASE_COMPLETE);
    jdbc.update(
        "INSERT INTO EDIN_20_CAP_H_PREV_PRACT (C_C, ESTADO, TC, H06) VALUES (?, 1, 1, '1')",
        CASE_COMPLETE);
    jdbc.update(
        "INSERT INTO EDIN_20_CAP_B_UBICA (C_C, ESTADO, TC, B07) VALUES (?, 1, 1, '1')",
        CASE_INCOMPLETE);

    CoverageTotals after =
        entity(department(report(ReportSurvey.GENERAL), 5), ENTITY_MIXED).totals();

    assertThat(after).isEqualTo(before);
    assertTotals(after, 9, 2, 2, 2);
    // Las filas repetidas las borra clearFixture (clear elimina los capítulos por rango de C_C).
  }

  @Test
  void yearWhoseChapterHLacksH06IsUnavailable() {
    jdbc.execute(
        "CREATE TABLE " + H_WITHOUT_H06 + " (C_C VARCHAR2(11), ESTADO NUMBER(1), TC NUMBER(1))");
    RulesFileRepository rulesFiles = mock(RulesFileRepository.class);
    when(rulesFiles.find("EDIN", 2027))
        .thenReturn(
            Optional.of(RuleSet.empty("EDIN", 2027).withTables(Map.of("H", H_WITHOUT_H06))));
    CoverageService isolated =
        new CoverageService(
            repository, new AnswerTableResolver(namedJdbc, survey, rulesFiles), survey, clock);

    assertThatThrownBy(() -> isolated.coverage(fixtureScope, ReportSurvey.GENERAL, 2027, null))
        .isInstanceOf(ReportUnavailableException.class)
        .hasMessage(
            "El cuestionario de 2027 no tiene las preguntas B07 y H06 que definen una encuesta"
                + " completa.");
  }
}
