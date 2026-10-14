package co.gov.dane.edi.security;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class SecurityConfigIT extends OracleIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;

  @Test
  void healthIsPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void csrfEndpointIssuesTokenAndCookie() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(cookie().exists("XSRF-TOKEN"));
  }

  @Test
  void protectedEndpointWithoutSessionReturnsProblem401() throws Exception {
    mockMvc
        .perform(get("/api/v1/survey/probe"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Sesión requerida"));
  }

  @Test
  void respondentReachesSurveyButNotAdministration() throws Exception {
    Cookie session = sessionFor(SessionPrincipal.respondent("1000000001"), Instant.now());
    mockMvc.perform(get("/api/v1/survey/probe").cookie(session)).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/admin/probe").cookie(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Acceso denegado"));
  }

  @Test
  void territorialAdministratorReachesAdministrationButNotSurvey() throws Exception {
    Cookie session =
        sessionFor(
            SessionPrincipal.administrator("adm.bogota", EnumSet.of(Role.ADMIN_TERRITORIAL), "11"),
            Instant.now());
    mockMvc.perform(get("/api/v1/admin/probe").cookie(session)).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/survey/probe").cookie(session)).andExpect(status().isForbidden());
  }

  @Test
  void stateChangingRequestRequiresCsrfToken() throws Exception {
    Cookie session = sessionFor(SessionPrincipal.respondent("1000000001"), Instant.now());
    mockMvc.perform(post("/api/v1/survey/probe").cookie(session)).andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/survey/probe").cookie(session).with(realCsrf(mockMvc)))
        .andExpect(status().isOk());
  }

  @Test
  void tamperedTokenIsRejectedAndCookieCleared() throws Exception {
    Cookie session = sessionFor(SessionPrincipal.respondent("1000000001"), Instant.now());
    Cookie tampered = new Cookie(SessionTokenService.SESSION_COOKIE_NAME, session.getValue() + "x");
    mockMvc
        .perform(get("/api/v1/survey/probe").cookie(tampered))
        .andExpect(status().isUnauthorized())
        .andExpect(cookie().maxAge(SessionTokenService.SESSION_COOKIE_NAME, 0));
  }

  @Test
  void publicEndpointIgnoresInvalidSessionCookie() throws Exception {
    Cookie garbage = new Cookie(SessionTokenService.SESSION_COOKIE_NAME, "no-es-un-jwt");
    mockMvc.perform(get("/api/v1/auth/csrf").cookie(garbage)).andExpect(status().isOk());
  }

  @Test
  void renewsSessionCloseToExpiry() throws Exception {
    Cookie oldSession =
        sessionFor(SessionPrincipal.respondent("1000000001"), Instant.now().minusSeconds(20 * 60));
    mockMvc
        .perform(get("/api/v1/survey/probe").cookie(oldSession))
        .andExpect(status().isOk())
        .andExpect(cookie().exists(SessionTokenService.SESSION_COOKIE_NAME));
  }

  @Test
  void doesNotRenewFreshSession() throws Exception {
    Cookie session = sessionFor(SessionPrincipal.respondent("1000000001"), Instant.now());
    mockMvc
        .perform(get("/api/v1/survey/probe").cookie(session))
        .andExpect(cookie().doesNotExist(SessionTokenService.SESSION_COOKIE_NAME));
  }

  @Test
  void securityHeadersArePresent() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/csrf"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "same-origin"))
        .andExpect(
            header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
  }

  @Test
  void unauthorizedResponseDoesNotLeakInternals() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/probe"))
        .andExpect(content().string(not(containsString("Exception"))));
  }

  @Test
  void accessDeniedThrownInsideControllerIsForbiddenProblemNotServerError() throws Exception {
    Cookie session = sessionFor(SessionPrincipal.respondent("1000000001"), Instant.now());
    mockMvc
        .perform(get("/api/v1/survey/probe-denied").cookie(session))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Acceso denegado"));
  }

  private Cookie sessionFor(SessionPrincipal principal, Instant issuedAt) {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME, tokens.issueToken(principal, issuedAt));
  }
}
