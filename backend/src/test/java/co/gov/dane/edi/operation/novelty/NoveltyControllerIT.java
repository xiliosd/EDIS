package co.gov.dane.edi.operation.novelty;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_COMPLETE_WITH_NOVELTY;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NOT_STARTED;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NOVELTY_OTHER_CAUSE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NO_CATALOG_WITH_KEY;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_ENGINEER;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_OPERATIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_TERRITORIAL_MULTI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationalAccessService;
import co.gov.dane.edi.operation.controller.NoveltyController;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OperationalSampleFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gestión de novedades por HTTP. Corre con el cuestionario en solo lectura (como producción): las
 * novedades deben funcionar igual.
 */
@TestPropertySource(properties = "app.admin.questionnaire-writes-enabled=false")
class NoveltyControllerIT extends OracleIntegrationTest {

  private static final String BASE = "/api/v1/admin/novelties";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private OperationalAccessService accessService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SurveyProperties survey;
  @Autowired private JsonMapper jsonMapper;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

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
  void territorialManagesNoveltiesInReadOnlyQuestionnaireMode() throws Exception {
    getAs(territorial(), "/catalog?anio=" + survey.year())
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(jsonPath("$[?(@.code == 8)].requiresText").value(true))
        .andExpect(jsonPath("$[?(@.code == 3)].name").value("Retiro ficticio"));
    getAs(territorial(), "/entities?departamento=5")
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(jsonPath("$[0].code").value(900001))
        .andExpect(jsonPath("$[0].sampleSize").value(9))
        .andExpect(jsonPath("$[0].withNovelty").value(3));
    postAs(territorial(), "/people", Map.of("departamento", 5, "entidad", 900001))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(jsonPath("$.length()").value(9))
        .andExpect(
            jsonPath("$[?(@.respondentCode == '" + CASE_NOVELTY_OTHER_CAUSE + "')].otherCause")
                .value(OperationalSampleFixture.OTHER_CAUSE_TEXT));

    postAs(territorial(), "/assign", assignBody(CASE_NOT_STARTED, 8, "Traslado “ficticio”"))
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    assertThat(storedCode(CASE_NOT_STARTED)).isEqualTo(8);
    postAs(
            territorial(),
            "/remove",
            Map.of(
                "anio",
                survey.year(),
                "respondentCode",
                CASE_NOT_STARTED,
                "expectedNoveltyCode",
                8,
                "expectedOtherCause",
                "Traslado “ficticio”"))
        .andExpect(status().isNoContent());
    assertThat(storedCode(CASE_NOT_STARTED)).isNull();
  }

  @Test
  void errorsAreProblemDetailsWithoutTheRespondentCode() throws Exception {
    MvcResult invalid =
        postAs(territorial(), "/assign", assignBody(CASE_NOT_STARTED, 8, " "))
            .andExpect(status().isUnprocessableContent())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
            .andExpect(jsonPath("$.errors.otherCause").exists())
            .andReturn();
    MvcResult conflict =
        postAs(territorial(), "/assign", assignBody(CASE_COMPLETE_WITH_NOVELTY, 3, null))
            .andExpect(status().isConflict())
            .andReturn();
    MvcResult notFound =
        postAs(territorial(), "/assign", assignBody(CASE_NO_CATALOG_WITH_KEY, 3, null))
            .andExpect(status().isNotFound())
            .andReturn();
    for (MvcResult result : List.of(invalid, conflict, notFound)) {
      assertThat(result.getResponse().getContentAsString()).doesNotContain("9000000");
    }
    postAs(territorial(), "/assign", Map.of("anio", survey.year(), "noveltyCode", 3))
        .andExpect(status().isBadRequest());
    getAs(territorial(), "/entities?departamento=76").andExpect(status().isForbidden());
  }

  @Test
  void otherRolesAreForbidden() throws Exception {
    for (Cookie cookie :
        List.of(
            session(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null),
            session(USER_ENGINEER, Role.ADMIN_NACIONAL, null))) {
      getAs(cookie, "/catalog").andExpect(status().isForbidden());
      getAs(cookie, "/entities?departamento=5").andExpect(status().isForbidden());
      postAs(cookie, "/people", Map.of("departamento", 5, "entidad", 900001))
          .andExpect(status().isForbidden());
      postAs(cookie, "/assign", assignBody(CASE_NOT_STARTED, 3, null))
          .andExpect(status().isForbidden());
      postAs(
              cookie,
              "/remove",
              Map.of("anio", survey.year(), "respondentCode", CASE_COMPLETE_WITH_NOVELTY))
          .andExpect(status().isForbidden());
    }
    assertThat(storedCode(CASE_NOT_STARTED)).isNull();
    assertThat(storedCode(CASE_COMPLETE_WITH_NOVELTY)).isEqualTo(3);
  }

  @Test
  void writesWithoutCsrfAreForbidden() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/assign")
                .cookie(territorial())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(assignBody(CASE_NOT_STARTED, 3, null))))
        .andExpect(status().isForbidden());
    assertThat(storedCode(CASE_NOT_STARTED)).isNull();
  }

  /** Las URL quedan en los logs de acceso: la cédula solo viaja en el cuerpo JSON. */
  @Test
  void respondentCodeNeverTravelsInTheUrl() {
    Set<String> allowedQueryParameters = Set.of("anio", "departamento");
    int checked = 0;
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
        handlerMapping.getHandlerMethods().entrySet()) {
      HandlerMethod handler = entry.getValue();
      if (!handler.getBeanType().equals(NoveltyController.class)) {
        continue;
      }
      checked++;
      assertThat(entry.getKey().getPatternValues())
          .allSatisfy(p -> assertThat(p).doesNotContain("{"));
      for (MethodParameter parameter : handler.getMethodParameters()) {
        assertThat(parameter.hasParameterAnnotation(PathVariable.class)).isFalse();
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
          assertThat(requestParam.name()).isIn(allowedQueryParameters);
        }
        if (parameter.hasParameterAnnotation(RequestBody.class)) {
          assertThat(entry.getKey().getMethodsCondition().getMethods())
              .extracting(Enum::name)
              .containsExactly("POST");
        }
      }
    }
    assertThat(checked).isEqualTo(5);
  }

  // ---- utilidades -------------------------------------------------------------------------

  private Map<String, Object> assignBody(String code, int noveltyCode, String otherCause) {
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("anio", survey.year());
    body.put("respondentCode", code);
    body.put("noveltyCode", noveltyCode);
    body.put("otherCause", otherCause);
    return body;
  }

  private Integer storedCode(String code) {
    java.math.BigDecimal value =
        jdbc.queryForObject(
            "SELECT IDFNOV FROM EDIN_MUESTRA WHERE C_C = ? AND ANIO = ?",
            java.math.BigDecimal.class,
            code,
            String.valueOf(survey.year()));
    return value == null ? null : value.intValueExact();
  }

  private Cookie territorial() {
    return session(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5");
  }

  private Cookie session(String username, Role role, String department) {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(SessionPrincipal.administrator(username, EnumSet.of(role), department)));
  }

  private ResultActions getAs(Cookie cookie, String path) throws Exception {
    return mockMvc.perform(get(BASE + path).cookie(cookie));
  }

  private ResultActions postAs(Cookie cookie, String path, Map<String, Object> body)
      throws Exception {
    MockHttpServletRequestBuilder builder =
        post(BASE + path)
            .with(realCsrf(mockMvc))
            .cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body));
    return mockMvc.perform(builder);
  }

  private String json(Map<String, Object> body) {
    return jsonMapper.writeValueAsString(body);
  }
}
