package co.gov.dane.edi.operation;

import static co.gov.dane.edi.support.OperationalSampleFixture.USER_ENGINEER;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_OPERATIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_TERRITORIAL_MULTI;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OperationalSampleFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.EnumSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** GET /api/v1/admin/operation/scope por rol. */
class OperationalScopeIT extends OracleIntegrationTest {

  private static final String SCOPE = "/api/v1/admin/operation/scope";

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
  void territorialSeesOnlyAssignedDepartments() throws Exception {
    getAs(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allDepartments").value(false))
        .andExpect(jsonPath("$.departments.length()").value(2))
        .andExpect(jsonPath("$.departments[0].code").value(5))
        .andExpect(jsonPath("$.departments[1].code").value(8))
        .andExpect(jsonPath("$.canManageNovelties").value(true))
        .andExpect(jsonPath("$.canManageKeys").value(false))
        .andExpect(jsonPath("$.defaultYear").value(survey.year()));
  }

  @Test
  void operativeSeesEverythingButManagesNothing() throws Exception {
    getAs(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allDepartments").value(true))
        .andExpect(jsonPath("$.departments[?(@.code == 76)]").isNotEmpty())
        .andExpect(jsonPath("$.canManageNovelties").value(false))
        .andExpect(jsonPath("$.canManageKeys").value(false));
  }

  @Test
  void nationalManagesKeys() throws Exception {
    getAs(USER_ENGINEER, Role.ADMIN_NACIONAL, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allDepartments").value(true))
        .andExpect(jsonPath("$.canManageKeys").value(true));
  }

  @Test
  void questionnaireAdministratorIsForbidden() throws Exception {
    getAs("adm.cuest", Role.ADMIN_CUESTIONARIO, null).andExpect(status().isForbidden());
  }

  @Test
  void anonymousIsUnauthorized() throws Exception {
    mockMvc.perform(get(SCOPE)).andExpect(status().isUnauthorized());
  }

  @Test
  void roleOnlyRoutesAreClosedToOtherRoles() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/keys/x").cookie(cookie("adm.op", Role.ADMIN_OPERATIVO, null)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/admin/novelties/x").cookie(cookie("adm.nac", Role.ADMIN_NACIONAL, null)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/admin/reports/x").cookie(cookie("adm.cu", Role.ADMIN_CUESTIONARIO, null)))
        .andExpect(status().isForbidden());
  }

  private ResultActions getAs(String username, Role role, String department) throws Exception {
    return mockMvc.perform(get(SCOPE).cookie(cookie(username, role, department)));
  }

  private Cookie cookie(String username, Role role, String department) {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(SessionPrincipal.administrator(username, EnumSet.of(role), department)));
  }
}
