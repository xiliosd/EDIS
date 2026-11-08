package co.gov.dane.edi.operation.report;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_OPERATIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_TERRITORIAL_MULTI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.EnumSet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Cortes de cobertura. Corre con el cuestionario en solo lectura (como producción): generar un
 * corte debe funcionar igual. El directorio de cortes es temporal (application-test.yml).
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "app.admin.questionnaire-writes-enabled=false")
class CoverageCutIT extends OracleIntegrationTest {

  private static final String CUTS = "/api/v1/admin/reports/cuts";
  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private OperationalAccessService accessService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SurveyProperties survey;

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
  void territorialGeneratesCutInReadOnlyModeWithAuditLineWithoutNames(CapturedOutput output)
      throws Exception {
    MvcResult result =
        create(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", "GENERAL", 5)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.departmentCode").value(5))
            .andExpect(jsonPath("$.departmentName").value("DEPARTMENT_ANTIOQUIA (ficticio)"))
            .andExpect(jsonPath("$.survey").value("GENERAL"))
            .andExpect(jsonPath("$.year").value(survey.year()))
            .andExpect(jsonPath("$.generatedBy").value(USER_TERRITORIAL_MULTI))
            .andExpect(
                jsonPath("$.id")
                    .value(org.hamcrest.Matchers.matchesRegex("[0-9]{8}-[0-9]{6}-[0-9a-f]{6}")))
            // Ruling 6: la hora se muestra en Bogotá.
            .andExpect(jsonPath("$.generatedAt").value(org.hamcrest.Matchers.endsWith("-05:00")))
            .andReturn();
    String id = idOf(result);

    assertThat(output.getAll())
        .contains("operacion=report-cut-create")
        .contains("usuario=" + USER_TERRITORIAL_MULTI)
        .contains("codigos=[5, " + id + "]")
        .doesNotContain("Persona Ficticia");
  }

  @Test
  void territorialSeesOwnCutAndNotTheOneOfAnotherDepartment() throws Exception {
    String own =
        idOf(create(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, "GENERAL", 76).andReturn());
    String antioquia =
        idOf(create(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", "GENERAL", 5).andReturn());

    getAs(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", CUTS + "?anio=" + survey.year())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + antioquia + "')]").isNotEmpty())
        .andExpect(jsonPath("$[?(@.id == '" + own + "')]").isEmpty());
    getAs(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", CUTS + "?departamento=76")
        .andExpect(status().isForbidden());
    getAs(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", CUTS + "/" + own + "/excel")
        .andExpect(status().isForbidden());
    getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, CUTS + "?anio=" + survey.year())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + own + "')]").isNotEmpty())
        .andExpect(jsonPath("$[?(@.id == '" + antioquia + "')]").isNotEmpty());
  }

  @Test
  void territorialCannotGenerateCutOfAnotherDepartment() throws Exception {
    create(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", "GENERAL", 76)
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidRequestIsBadRequest() throws Exception {
    mockMvc
        .perform(
            post(CUTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"encuesta\":\"GENERAL\"}")
                .with(realCsrf(mockMvc))
                .cookie(cookie(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void excelOfTheCutDownloadsWithFiguresAndNoStore() throws Exception {
    String id =
        idOf(create(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", "GENERAL", 5).andReturn());

    MvcResult result =
        getAs(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5", CUTS + "/" + id + "/excel")
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentType()).isEqualTo(XLSX);
    assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
        .contains("attachment")
        .contains("corte-cobertura-" + id + ".xlsx");
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
      assertThat(workbook.getSheet("Cobertura")).isNotNull();
      assertThat(workbook.getSheet("Consolidado del alcance")).isNotNull();
    }
  }

  @Test
  void unknownOrInvalidIdIsNotFound() throws Exception {
    getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, CUTS + "/20200101-000000-abcdef/excel")
        .andExpect(status().isNotFound());
    getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, CUTS + "/..%2Fx/excel")
        // El cortafuegos de Spring rechaza la barra codificada antes de llegar al controlador.
        .andExpect(status().is4xxClientError());
    getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null, CUTS + "/no-es-un-id/excel")
        .andExpect(status().isNotFound());
  }

  @Test
  void questionnaireAdministratorIsForbidden() throws Exception {
    create("adm.cuest", Role.ADMIN_CUESTIONARIO, null, "GENERAL", 5)
        .andExpect(status().isForbidden());
    getAs("adm.cuest", Role.ADMIN_CUESTIONARIO, null, CUTS).andExpect(status().isForbidden());
  }

  private static String idOf(MvcResult result) throws Exception {
    String body = result.getResponse().getContentAsString();
    int start = body.indexOf("\"id\":\"") + 6;
    return body.substring(start, body.indexOf('"', start));
  }

  private ResultActions create(
      String username, Role role, String department, String survey, int departmentCode)
      throws Exception {
    return mockMvc.perform(
        post(CUTS)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"encuesta\":\"" + survey + "\",\"departamento\":" + departmentCode + "}")
            .with(realCsrf(mockMvc))
            .cookie(cookie(username, role, department)));
  }

  private ResultActions getAs(String username, Role role, String department, String url)
      throws Exception {
    return mockMvc.perform(get(url).cookie(cookie(username, role, department)));
  }

  private Cookie cookie(String username, Role role, String department) {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(SessionPrincipal.administrator(username, EnumSet.of(role), department)));
  }
}
