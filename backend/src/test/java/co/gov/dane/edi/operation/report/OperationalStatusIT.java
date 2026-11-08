package co.gov.dane.edi.operation.report;

import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_AT_SIGN_ENTITY;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_COMPLETE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_COMPLETE_WITH_NOVELTY;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_DMC_WITH_KEY;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_EDID_COMPLETE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_FORMULA_NAME;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_INACTIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_INCOMPLETE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NOT_STARTED;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NOVELTY_NOT_STARTED;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NOVELTY_OTHER_CAUSE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NO_CATALOG_WITH_KEY;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NO_KEY_DMC;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NO_KEY_WEB;
import static co.gov.dane.edi.support.OperationalSampleFixture.ENTITY_NO_CATALOG;
import static co.gov.dane.edi.support.OperationalSampleFixture.FORMULA_NAME;
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
import java.util.Set;
import org.apache.poi.ss.usermodel.CellType;
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

/** Listado nominal "Estado de mi operativo" por HTTP: filtros, paginación, acceso y Excel. */
class OperationalStatusIT extends OracleIntegrationTest {

  private static final String STATUS = "/api/v1/admin/reports/operational-status";
  private static final ObjectMapper JSON = new ObjectMapper();

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
  void everyFilterReturnsExactlyTheFixtureCases() throws Exception {
    assertThat(codes("ALL"))
        .containsExactlyInAnyOrder(
            CASE_COMPLETE,
            CASE_COMPLETE_WITH_NOVELTY,
            CASE_INCOMPLETE,
            CASE_NOT_STARTED,
            CASE_NOVELTY_NOT_STARTED,
            CASE_NOVELTY_OTHER_CAUSE,
            CASE_DMC_WITH_KEY,
            CASE_NO_KEY_DMC,
            CASE_FORMULA_NAME);
    assertThat(codes("COMPLETE"))
        .containsExactlyInAnyOrder(CASE_COMPLETE, CASE_COMPLETE_WITH_NOVELTY);
    assertThat(codes("INCOMPLETE")).containsExactlyInAnyOrder(CASE_INCOMPLETE, CASE_DMC_WITH_KEY);
    assertThat(codes("NOVELTY"))
        .containsExactlyInAnyOrder(CASE_NOVELTY_NOT_STARTED, CASE_NOVELTY_OTHER_CAUSE);
    assertThat(codes("NOT_STARTED"))
        .containsExactlyInAnyOrder(CASE_NOT_STARTED, CASE_NO_KEY_DMC, CASE_FORMULA_NAME);
    assertThat(codes("ALL")).doesNotContain(CASE_INACTIVE);
  }

  @Test
  void completeWithNoveltyIsCompleteAndShowsItsNovelty() throws Exception {
    JsonNode row = rowOf(page("estado=COMPLETE"), CASE_COMPLETE_WITH_NOVELTY);
    assertThat(row.get("status").asText()).isEqualTo("COMPLETE");
    assertThat(row.get("noveltyCode").asInt()).isEqualTo(3);
    assertThat(row.get("noveltyName").asText()).isEqualTo("Retiro ficticio");
    assertThat(row.get("completedAt").isNull()).isFalse();
    assertThat(row.get("level").asInt()).isEqualTo(2);
    assertThat(row.get("loadType").asText()).isEqualTo("WEB");
    assertThat(chaptersOf(row)).containsExactly("C", "D", "E", "F", "G");
  }

  @Test
  void rowsCarryOtherCauseLoadTypeAndMissingChapters() throws Exception {
    JsonNode page = page("estado=ALL");
    JsonNode other = rowOf(page, CASE_NOVELTY_OTHER_CAUSE);
    assertThat(other.get("status").asText()).isEqualTo("NOVELTY");
    assertThat(other.get("noveltyCode").asInt()).isEqualTo(8);
    assertThat(other.get("otherCause").asText()).isEqualTo(OTHER_CAUSE_TEXT);
    assertThat(other.get("completedAt").isNull()).isTrue();
    assertThat(chaptersOf(other)).containsExactly("B", "C", "D", "E", "F", "G", "H");
    JsonNode dmc = rowOf(page, CASE_DMC_WITH_KEY);
    assertThat(dmc.get("loadType").asText()).isEqualTo("DMC");
    assertThat(dmc.get("status").asText()).isEqualTo("INCOMPLETE");
    assertThat(chaptersOf(dmc)).containsExactly("C", "D", "E", "F", "G", "H");
    assertThat(rowOf(page, CASE_NOT_STARTED).get("noveltyCode").isNull()).isTrue();
  }

  @Test
  void notStartedRespectsTheDepartmentInJsonAndExcel() throws Exception {
    // Dept 76: solo CASE_NO_KEY_WEB sin iniciar; los sin iniciar del 5 no se cuelan.
    JsonNode valle = page("departamento=76&estado=NOT_STARTED");
    assertThat(codesOf(valle)).containsExactly(CASE_NO_KEY_WEB);
    assertThat(valle.get("total").asLong()).isEqualTo(1);
    MvcResult excel = excel("departamento=76&estado=NOT_STARTED");
    try (XSSFWorkbook workbook = open(excel)) {
      Sheet sheet = workbook.getSheet("Estado operativo");
      assertThat(sheet).isNotNull();
      assertThat(dataRows(sheet)).hasSize(1);
      assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo(CASE_NO_KEY_WEB);
    }
  }

  @Test
  void paginationSplitsTheListWithoutRepeatingRows() throws Exception {
    List<String> seen = new ArrayList<>();
    for (int page = 0; page < 5; page++) {
      JsonNode result = page("estado=ALL&size=2&page=" + page);
      assertThat(result.get("total").asLong()).isEqualTo(9);
      assertThat(result.get("size").asInt()).isEqualTo(2);
      assertThat(result.get("page").asInt()).isEqualTo(page);
      assertThat(result.get("rows").size()).isEqualTo(page < 4 ? 2 : 1);
      seen.addAll(codesOf(result));
    }
    assertThat(seen).hasSize(9).doesNotHaveDuplicates();
    assertThat(page("estado=ALL&size=2&page=5").get("rows").size()).isZero();
  }

  @Test
  void invalidPagingIsBadRequestProblem() throws Exception {
    MvcResult tooBig = asOperative(USER_OPERATIVE, "?departamento=5&size=500");
    assertThat(tooBig.getResponse().getStatus()).isEqualTo(400);
    assertThat(tooBig.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(asOperative(USER_OPERATIVE, "?departamento=5&page=-1").getResponse().getStatus())
        .isEqualTo(400);
    assertThat(asOperative(USER_OPERATIVE, "?departamento=5&size=0").getResponse().getStatus())
        .isEqualTo(400);
    assertThat(asOperative(USER_OPERATIVE, "").getResponse().getStatus()).isEqualTo(400);
    assertThat(asOperative(USER_OPERATIVE, "?departamento=5&estado=OTRO").getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void foreignDepartmentIsForbiddenInJsonAndExcel() throws Exception {
    assertThat(
            getAs(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", "?departamento=76")
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(
            getAs(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", "/excel?departamento=76")
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(
            getAs(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", "?departamento=8")
                .getResponse()
                .getStatus())
        .isEqualTo(200);
  }

  @Test
  void cuestionarioAdministratorIsForbidden() throws Exception {
    assertThat(
            getAs("adm.cuest", Role.ADMIN_CUESTIONARIO, null, "?departamento=5")
                .getResponse()
                .getStatus())
        .isEqualTo(403);
  }

  @Test
  void surveyAndEntityFiltersApply() throws Exception {
    assertThat(codesOf(page("departamento=8&encuesta=EDI"))).containsExactly(CASE_AT_SIGN_ENTITY);
    assertThat(codesOf(page("departamento=8&encuesta=EDID"))).containsExactly(CASE_EDID_COMPLETE);
    JsonNode noCatalog = page("departamento=76&entidad=" + ENTITY_NO_CATALOG);
    assertThat(codesOf(noCatalog))
        .containsExactlyInAnyOrder(CASE_NO_CATALOG_WITH_KEY, CASE_NO_KEY_WEB);
    assertThat(noCatalog.get("rows").get(0).get("entityName").asText())
        .isEqualTo("Entidad " + ENTITY_NO_CATALOG);
    assertThat(page("estado=ALL&entidad=" + ENTITY_NO_CATALOG).get("total").asLong()).isZero();
  }

  @Test
  void responsesAreNotCached() throws Exception {
    MvcResult json = asOperative(USER_OPERATIVE, "?departamento=5");
    assertThat(json.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    MvcResult excel = excel("departamento=5");
    assertThat(excel.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
  }

  @Test
  void excelHasNameHeadersAllRowsAndTextOnlyCells() throws Exception {
    MvcResult excel = excel("departamento=5&estado=ALL");
    String today = LocalDate.now(clock.withZone(ZoneId.of("America/Bogota"))).toString();
    assertThat(excel.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
        .contains("attachment")
        .contains("estado-operativo-general-" + survey.year() + "-dpto05-" + today + ".xlsx");
    try (XSSFWorkbook workbook = open(excel)) {
      Sheet sheet = workbook.getSheet("Estado operativo");
      assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Cédula");
      assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo("Estado");
      assertThat(dataRows(sheet)).hasSize(9);
      Set<String> formulaNames = new java.util.HashSet<>();
      for (Row row : dataRows(sheet)) {
        if (row.getCell(3) != null
            && row.getCell(3).getCellType() == CellType.STRING
            && row.getCell(3).getStringCellValue().equals(FORMULA_NAME)) {
          formulaNames.add(row.getCell(3).getStringCellValue());
        }
      }
      assertThat(formulaNames).containsExactly(FORMULA_NAME);
    }
  }

  // ---- utilidades -------------------------------------------------------------------------

  private List<String> codes(String statusFilter) throws Exception {
    return codesOf(page("estado=" + statusFilter));
  }

  private JsonNode page(String query) throws Exception {
    String withDepartment = query.contains("departamento=") ? query : "departamento=5&" + query;
    MvcResult result = asOperative(USER_OPERATIVE, "?" + withDepartment);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return JSON.readTree(result.getResponse().getContentAsString());
  }

  private MvcResult excel(String query) throws Exception {
    MvcResult result = asOperative(USER_OPERATIVE, "/excel?" + query);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return result;
  }

  private static List<String> codesOf(JsonNode page) {
    List<String> codes = new ArrayList<>();
    page.get("rows").forEach(row -> codes.add(row.get("respondentCode").asText()));
    return codes;
  }

  private static JsonNode rowOf(JsonNode page, String code) {
    for (JsonNode row : page.get("rows")) {
      if (code.equals(row.get("respondentCode").asText())) {
        return row;
      }
    }
    throw new AssertionError("No aparece el caso " + code);
  }

  private static List<String> chaptersOf(JsonNode row) {
    List<String> chapters = new ArrayList<>();
    row.get("missingChapters").forEach(chapter -> chapters.add(chapter.asText()));
    return chapters;
  }

  private static List<Row> dataRows(Sheet sheet) {
    List<Row> rows = new ArrayList<>();
    for (int index = 2; index <= sheet.getLastRowNum(); index++) {
      if (sheet.getRow(index) != null) {
        rows.add(sheet.getRow(index));
      }
    }
    return rows;
  }

  private static XSSFWorkbook open(MvcResult result) throws Exception {
    return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
  }

  private MvcResult asOperative(String username, String suffix) throws Exception {
    return getAs(username, Role.ADMIN_OPERATIVO, null, suffix);
  }

  private MvcResult getAs(String username, Role role, String department, String suffix)
      throws Exception {
    Cookie cookie =
        new Cookie(
            SessionTokenService.SESSION_COOKIE_NAME,
            tokens.issueToken(
                SessionPrincipal.administrator(username, EnumSet.of(role), department)));
    ResultActions actions = mockMvc.perform(get(STATUS + suffix).cookie(cookie));
    return actions.andReturn();
  }
}
