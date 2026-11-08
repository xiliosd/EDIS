package co.gov.dane.edi.operation.report;

import static co.gov.dane.edi.support.OperationalSampleFixture.USER_ENGINEER;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_OPERATIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_TERRITORIAL_MULTI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationalAccessService;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OperationalSampleFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Reporte de cobertura por HTTP: JSON por rol, Excel, validación y años. */
class CoverageReportIT extends OracleIntegrationTest {

  private static final String COVERAGE = "/api/v1/admin/reports/coverage";
  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private OperationalAccessService accessService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SurveyProperties survey;
  @Autowired private Clock clock;

  private OperationalSampleFixture fixture;

  @BeforeEach
  void loadFixture() {
    fixture = new OperationalSampleFixture(jdbc);
    fixture.load(survey.year());
    accessService.clearCache();
  }

  @AfterEach
  void clearFixture() {
    fixture.clear(survey.year());
    accessService.clearCache();
  }

  @Test
  void operativeSeesTheThreeFixtureDepartments() throws Exception {
    getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, COVERAGE + "?encuesta=GENERAL")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.survey").value("GENERAL"))
        .andExpect(jsonPath("$.year").value(survey.year()))
        .andExpect(jsonPath("$.departments[?(@.code == 5)]").isNotEmpty())
        .andExpect(jsonPath("$.departments[?(@.code == 8)]").isNotEmpty())
        .andExpect(jsonPath("$.departments[?(@.code == 76)]").isNotEmpty())
        .andExpect(jsonPath("$.departments[?(@.code == 5)].totals.sample").value(9));
  }

  @Test
  void territorialWithSeveralDepartmentsSeesOnlyAssignedOnes() throws Exception {
    getAs(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", COVERAGE + "?encuesta=GENERAL")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.departments.length()").value(2))
        .andExpect(jsonPath("$.departments[?(@.code == 5)]").isNotEmpty())
        .andExpect(jsonPath("$.departments[?(@.code == 8)]").isNotEmpty())
        .andExpect(jsonPath("$.departments[?(@.code == 76)]").isEmpty());
  }

  @Test
  void territorialAskingForAnotherDepartmentIsForbidden() throws Exception {
    getAs(
            USER_TERRITORIAL_MULTI,
            Role.ADMIN_TERRITORIAL,
            "5",
            COVERAGE + "?encuesta=GENERAL&departamento=76")
        .andExpect(status().isForbidden());
    getAs(
            USER_TERRITORIAL_MULTI,
            Role.ADMIN_TERRITORIAL,
            "5",
            COVERAGE + "/excel?encuesta=GENERAL&departamento=76")
        .andExpect(status().isForbidden());
  }

  @Test
  void yearDefaultsToConfiguredYear() throws Exception {
    getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, COVERAGE)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.year").value(survey.year()))
        .andExpect(jsonPath("$.survey").value("GENERAL"));
  }

  @Test
  void unknownSurveyIsBadRequestProblem() throws Exception {
    getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, COVERAGE + "?encuesta=OTRA")
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentType())
                    .startsWith("application/problem+json"));
    getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, COVERAGE + "/excel?encuesta=OTRA")
        .andExpect(status().isBadRequest());
  }

  @Test
  void excelHasExpectedNameHeadersAndEntityFigures() throws Exception {
    MvcResult result =
        getAs(
                USER_OPERATIVE,
                Role.ADMIN_OPERATIVO,
                null,
                COVERAGE + "/excel?encuesta=EDI&departamento=5")
            .andExpect(status().isOk())
            .andReturn();
    String today = LocalDate.now(clock).toString();
    assertThat(result.getResponse().getContentType()).isEqualTo(XLSX);
    assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
        .contains("attachment")
        .contains("cobertura-edi-" + survey.year() + "-dpto05-" + today + ".xlsx");
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
      Sheet coverage = workbook.getSheet("Cobertura");
      Sheet national = workbook.getSheet("Consolidado nacional");
      assertThat(coverage).isNotNull();
      assertThat(national).isNotNull();
      Row header = coverage.getRow(1);
      assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Departamento");
      assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Nivel");
      assertThat(header.getCell(12).getStringCellValue()).isEqualTo("% sin diligenciar");
      assertThat(findEntityTotal(coverage, 900001)).isNotNull();
      assertThat(findRow(national, 0, "Total nacional")).isNotNull();
    }
  }

  @Test
  void excelCarriesEntityMixedTotalsForGeneralSurvey() throws Exception {
    MvcResult result =
        getAs(USER_ENGINEER, Role.ADMIN_NACIONAL, null, COVERAGE + "/excel?encuesta=GENERAL")
            .andExpect(status().isOk())
            .andReturn();
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
      Sheet coverage = workbook.getSheet("Cobertura");
      Row entityTotal = findEntityTotal(coverage, 900001);
      assertThat(entityTotal).as("fila Total de la entidad 900001").isNotNull();
      // ENTITY_MIXED: muestra 9, novedades 2, efectiva 7, completas 2, incompletas 2, sin 3.
      assertThat(entityTotal.getCell(4).getNumericCellValue()).isEqualTo(9d);
      assertThat(entityTotal.getCell(5).getNumericCellValue()).isEqualTo(2d);
      assertThat(entityTotal.getCell(6).getNumericCellValue()).isEqualTo(7d);
      assertThat(entityTotal.getCell(7).getNumericCellValue()).isEqualTo(2d);
      assertThat(entityTotal.getCell(9).getNumericCellValue()).isEqualTo(2d);
      assertThat(entityTotal.getCell(11).getNumericCellValue()).isEqualTo(3d);
    }
  }

  @Test
  void yearsContainConfiguredYearDescending() throws Exception {
    getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, "/api/v1/admin/reports/years")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@ == " + survey.year() + ")]").isNotEmpty())
        .andExpect(
            result -> {
              var years =
                  new com.fasterxml.jackson.databind.ObjectMapper()
                      .readTree(result.getResponse().getContentAsString());
              for (int index = 1; index < years.size(); index++) {
                assertThat(years.get(index - 1).asInt()).isGreaterThan(years.get(index).asInt());
              }
            });
  }

  @Test
  void questionnaireAdministratorIsForbidden() throws Exception {
    getAs("adm.cuest", Role.ADMIN_CUESTIONARIO, null, "/api/v1/admin/reports/years")
        .andExpect(status().isForbidden());
  }

  private static Row findRow(Sheet sheet, int column, String text) {
    for (Row row : sheet) {
      if (row.getCell(column) != null
          && row.getCell(column).getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
          && row.getCell(column).getStringCellValue().startsWith(text)) {
        return row;
      }
    }
    return null;
  }

  /** La fila de nivel "Total" de una entidad (columna 1 = código, columna 3 = nivel). */
  private static Row findEntityTotal(Sheet sheet, long entityCode) {
    for (Row row : sheet) {
      if (row.getCell(1) != null
          && row.getCell(1).getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
          && (long) row.getCell(1).getNumericCellValue() == entityCode
          && row.getCell(3) != null
          && "Total".equals(row.getCell(3).getStringCellValue())) {
        return row;
      }
    }
    return null;
  }

  private ResultActions getAs(String username, Role role, String department, String url)
      throws Exception {
    Cookie cookie =
        new Cookie(
            SessionTokenService.SESSION_COOKIE_NAME,
            tokens.issueToken(
                SessionPrincipal.administrator(username, EnumSet.of(role), department)));
    return mockMvc.perform(get(url).cookie(cookie));
  }
}
