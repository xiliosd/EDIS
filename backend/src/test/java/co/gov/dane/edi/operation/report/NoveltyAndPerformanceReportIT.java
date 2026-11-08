package co.gov.dane.edi.operation.report;

import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_COMPLETE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_COMPLETE_WITH_NOVELTY;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_DEPARTMENT_NO_CATALOG;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_DMC_WITH_KEY;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_EDID_COMPLETE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_ENCODED_OTHER_CAUSE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_INCOMPLETE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NOT_STARTED;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NOVELTY_NOT_STARTED;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NOVELTY_OTHER_CAUSE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NO_CATALOG_WITH_KEY;
import static co.gov.dane.edi.support.OperationalSampleFixture.DECODED_OTHER_CAUSE_TEXT;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_NO_CATALOG;
import static co.gov.dane.edi.support.OperationalSampleFixture.OTHER_CAUSE_TEXT;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_OPERATIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_TERRITORIAL_MULTI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationalAccessService;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OperationalSampleFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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

/** Reportes nominales de novedades y de rendimientos por HTTP: contenido, alcance y Excel. */
class NoveltyAndPerformanceReportIT extends OracleIntegrationTest {

  private static final String NOVELTIES = "/api/v1/admin/reports/novelties";
  private static final String PERFORMANCE = "/api/v1/admin/reports/performance";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int PERFORMANCE_CODE_COLUMN = 2;
  private static final int NOVELTY_CODE_COLUMN = 4;
  private static final int FIRST_CHAPTER_COLUMN = 5;
  private static final int CHAPTER_H_TIME_COLUMN = FIRST_CHAPTER_COLUMN + 2 * 6 + 1;

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
  void noveltiesIncludeOtherCauseTextAndMarkCompleteWithNovelty() throws Exception {
    JsonNode rows = json(asOperative(NOVELTIES, "?departamento=5"));
    assertThat(codesOf(rows))
        .containsExactlyInAnyOrder(
            CASE_COMPLETE_WITH_NOVELTY, CASE_NOVELTY_NOT_STARTED, CASE_NOVELTY_OTHER_CAUSE);
    JsonNode other = rowOf(rows, CASE_NOVELTY_OTHER_CAUSE);
    assertThat(other.get("noveltyCode").asInt()).isEqualTo(8);
    assertThat(other.get("otherCause").asText()).isEqualTo(OTHER_CAUSE_TEXT);
    assertThat(other.get("complete").asBoolean()).isFalse();
    assertThat(other.get("level").asInt()).isEqualTo(3);
    assertThat(other.get("departmentCode").asInt()).isEqualTo(5);
    assertThat(other.get("departmentName").asText()).contains("ficticio");
    JsonNode complete = rowOf(rows, CASE_COMPLETE_WITH_NOVELTY);
    assertThat(complete.get("complete").asBoolean()).isTrue();
    assertThat(complete.get("noveltyName").asText()).isEqualTo("Retiro ficticio");
    assertThat(complete.get("entityName").asText()).isEqualTo("Entidad Ficticia Mixta");
    assertThat(rowOf(rows, CASE_NOVELTY_NOT_STARTED).get("complete").asBoolean()).isFalse();
  }

  @Test
  void territorialSeesOnlyItsDepartmentsInNovelties() throws Exception {
    jdbc.update("UPDATE EDIN_MUESTRA SET IDFNOV = 3 WHERE C_C = ?", CASE_NO_CATALOG_WITH_KEY);
    List<String> national = codesOf(json(asOperative(NOVELTIES, "")));
    assertThat(national)
        .contains(
            CASE_COMPLETE_WITH_NOVELTY,
            CASE_NOVELTY_NOT_STARTED,
            CASE_NOVELTY_OTHER_CAUSE,
            CASE_NO_CATALOG_WITH_KEY);
    List<String> territorial = codesOf(json(asTerritorial(NOVELTIES, "")));
    assertThat(territorial)
        .containsExactlyInAnyOrder(
            CASE_COMPLETE_WITH_NOVELTY, CASE_NOVELTY_NOT_STARTED, CASE_NOVELTY_OTHER_CAUSE);
    assertThat(asTerritorial(NOVELTIES, "?departamento=76").getResponse().getStatus())
        .isEqualTo(403);
    assertThat(asTerritorial(NOVELTIES + "/excel", "?departamento=76").getResponse().getStatus())
        .isEqualTo(403);
  }

  @Test
  void performanceExcludesWhoNeverStartedAndKeepsChapterOrder() throws Exception {
    JsonNode rows = json(asOperative(PERFORMANCE, "?departamento=5"));
    assertThat(codesOf(rows))
        .containsExactlyInAnyOrder(
            CASE_COMPLETE, CASE_COMPLETE_WITH_NOVELTY, CASE_INCOMPLETE, CASE_DMC_WITH_KEY);
    JsonNode complete = rowOf(rows, CASE_COMPLETE);
    JsonNode chapters = complete.get("chapters");
    assertThat(chapters.size()).isEqualTo(7);
    List<String> order = new ArrayList<>();
    chapters.forEach(chapter -> order.add(chapter.get("chapter").asText()));
    assertThat(order).containsExactly("B", "C", "D", "E", "F", "G", "H");
    String year = String.valueOf(survey.year());
    assertThat(chapters.get(0).get("date").asText()).isEqualTo(year + "-03-10");
    assertThat(chapters.get(0).get("time").asText()).startsWith("09:01");
    assertThat(chapters.get(6).get("time").asText()).startsWith("10:01");
    assertThat(chapters.get(1).get("date").isNull()).isTrue();
    assertThat(chapters.get(1).get("time").isNull()).isTrue();
    // Incompleto: sin fila en H.
    JsonNode incomplete = rowOf(rows, CASE_INCOMPLETE).get("chapters");
    assertThat(incomplete.get(0).get("date").isNull()).isFalse();
    assertThat(incomplete.get(6).get("date").isNull()).isTrue();
  }

  @Test
  void territorialSeesOnlyItsDepartmentsInPerformance() throws Exception {
    List<String> national = codesOf(json(asOperative(PERFORMANCE, "")));
    assertThat(national).contains(CASE_COMPLETE, CASE_EDID_COMPLETE, CASE_NO_CATALOG_WITH_KEY);
    List<String> territorial = codesOf(json(asTerritorial(PERFORMANCE, "")));
    assertThat(territorial)
        .contains(CASE_COMPLETE, CASE_EDID_COMPLETE)
        .doesNotContain(CASE_NO_CATALOG_WITH_KEY, CASE_NOT_STARTED);
    assertThat(asTerritorial(PERFORMANCE, "?departamento=76").getResponse().getStatus())
        .isEqualTo(403);
    assertThat(asTerritorial(PERFORMANCE, "?departamento=8").getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  void performanceExcelPutsTheTimeInTheHourColumnOfEachChapter() throws Exception {
    MvcResult excel = asOperative(PERFORMANCE + "/excel", "?departamento=5");
    assertThat(excel.getResponse().getStatus()).isEqualTo(200);
    String today = LocalDate.now(clock.withZone(ZoneId.of("America/Bogota"))).toString();
    assertThat(excel.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
        .contains("rendimientos-general-" + survey.year() + "-dpto05-" + today + ".xlsx");
    try (XSSFWorkbook workbook = open(excel)) {
      Sheet sheet = workbook.getSheet("Rendimientos");
      Row header = sheet.getRow(1);
      assertThat(header.getCell(FIRST_CHAPTER_COLUMN).getStringCellValue()).isEqualTo("Fecha B");
      assertThat(header.getCell(FIRST_CHAPTER_COLUMN + 1).getStringCellValue()).isEqualTo("Hora B");
      assertThat(header.getCell(CHAPTER_H_TIME_COLUMN - 1).getStringCellValue())
          .isEqualTo("Fecha H");
      assertThat(header.getCell(CHAPTER_H_TIME_COLUMN).getStringCellValue()).isEqualTo("Hora H");
      Row row = rowByCode(sheet, CASE_COMPLETE, PERFORMANCE_CODE_COLUMN);
      assertThat(row.getCell(CHAPTER_H_TIME_COLUMN).getStringCellValue()).isEqualTo("10:01:00");
      assertThat(row.getCell(CHAPTER_H_TIME_COLUMN - 1).getLocalDateTimeCellValue().toLocalDate())
          .isEqualTo(LocalDate.of(survey.year(), 3, 10));
      assertThat(row.getCell(FIRST_CHAPTER_COLUMN + 1).getStringCellValue()).isEqualTo("09:01:00");
    }
  }

  @Test
  void otherCauseIsDecodedInNoveltiesJsonAndExcel() throws Exception {
    fixture.loadEdgeCases(survey.year());

    JsonNode rows = json(asOperative(NOVELTIES, "?departamento=5"));
    assertThat(rowOf(rows, CASE_ENCODED_OTHER_CAUSE).get("otherCause").asText())
        .isEqualTo(DECODED_OTHER_CAUSE_TEXT);
    try (XSSFWorkbook workbook = open(asOperative(NOVELTIES + "/excel", "?departamento=5"))) {
      Row encoded =
          rowByCode(workbook.getSheet("Novedades"), CASE_ENCODED_OTHER_CAUSE, NOVELTY_CODE_COLUMN);
      assertThat(encoded.getCell(9).getStringCellValue()).isEqualTo(DECODED_OTHER_CAUSE_TEXT);
    }
  }

  @Test
  void noveltyInADepartmentOutsideTheCatalogKeepsAGeneratedName() throws Exception {
    fixture.loadEdgeCases(survey.year());
    jdbc.update("UPDATE EDIN_MUESTRA SET IDFNOV = 3 WHERE C_C = ?", CASE_DEPARTMENT_NO_CATALOG);

    JsonNode row = rowOf(json(asOperative(NOVELTIES, "")), CASE_DEPARTMENT_NO_CATALOG);

    assertThat(row.get("departmentName").asText())
        .isEqualTo("Departamento " + DEPARTMENT_NO_CATALOG);
  }

  @Test
  void noveltiesExcelHasHeadersTextAndCompleteFlag() throws Exception {
    MvcResult excel = asOperative(NOVELTIES + "/excel", "?departamento=5");
    assertThat(excel.getResponse().getStatus()).isEqualTo(200);
    assertThat(excel.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
        .contains("novedades-general-" + survey.year() + "-dpto05-");
    try (XSSFWorkbook workbook = open(excel)) {
      Sheet sheet = workbook.getSheet("Novedades");
      assertThat(sheet.getRow(1).getCell(9).getStringCellValue()).isEqualTo("Otra causa");
      assertThat(sheet.getLastRowNum()).isEqualTo(4);
      Row other = rowByCode(sheet, CASE_NOVELTY_OTHER_CAUSE, NOVELTY_CODE_COLUMN);
      assertThat(other.getCell(9).getStringCellValue()).isEqualTo(OTHER_CAUSE_TEXT);
      assertThat(other.getCell(10).getStringCellValue()).isEqualTo("No");
      assertThat(
              rowByCode(sheet, CASE_COMPLETE_WITH_NOVELTY, NOVELTY_CODE_COLUMN)
                  .getCell(10)
                  .getStringCellValue())
          .isEqualTo("Sí");
    }
  }

  @Test
  void surveyFilterAppliesAndResponsesAreNotCached() throws Exception {
    assertThat(codesOf(json(asOperative(PERFORMANCE, "?departamento=8&encuesta=EDID"))))
        .containsExactly(CASE_EDID_COMPLETE);
    for (String path :
        List.of(NOVELTIES, PERFORMANCE, NOVELTIES + "/excel", PERFORMANCE + "/excel")) {
      MvcResult result = asOperative(path, "?departamento=5");
      assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }
  }

  @Test
  void cuestionarioAdministratorIsForbidden() throws Exception {
    for (String path : List.of(NOVELTIES, PERFORMANCE)) {
      assertThat(
              getAs("adm.cuest", Role.ADMIN_CUESTIONARIO, null, path, "").getResponse().getStatus())
          .isEqualTo(403);
    }
  }

  // ---- utilidades -------------------------------------------------------------------------

  private static JsonNode json(MvcResult result) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return JSON.readTree(result.getResponse().getContentAsString());
  }

  private static List<String> codesOf(JsonNode rows) {
    List<String> codes = new ArrayList<>();
    rows.forEach(row -> codes.add(row.get("respondentCode").asText()));
    return codes;
  }

  private static JsonNode rowOf(JsonNode rows, String code) {
    for (JsonNode row : rows) {
      if (code.equals(row.get("respondentCode").asText())) {
        return row;
      }
    }
    throw new AssertionError("No aparece el caso " + code);
  }

  /** Busca por cédula en la columna indicada entre las filas de datos. */
  private static Row rowByCode(Sheet sheet, String code, int codeColumn) {
    for (int index = 2; index <= sheet.getLastRowNum(); index++) {
      Row row = sheet.getRow(index);
      if (row != null && code.equals(row.getCell(codeColumn).getStringCellValue())) {
        return row;
      }
    }
    throw new AssertionError("No aparece el caso " + code);
  }

  private static XSSFWorkbook open(MvcResult result) throws Exception {
    return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
  }

  private MvcResult asOperative(String path, String query) throws Exception {
    return getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, path, query);
  }

  private MvcResult asTerritorial(String path, String query) throws Exception {
    return getAs(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", path, query);
  }

  private MvcResult getAs(String username, Role role, String department, String path, String query)
      throws Exception {
    Cookie cookie =
        new Cookie(
            SessionTokenService.SESSION_COOKIE_NAME,
            tokens.issueToken(
                SessionPrincipal.administrator(username, EnumSet.of(role), department)));
    return mockMvc.perform(get(path + query).cookie(cookie)).andReturn();
  }
}
