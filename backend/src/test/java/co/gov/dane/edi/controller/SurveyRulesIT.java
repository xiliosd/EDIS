package co.gov.dane.edi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class SurveyRulesIT extends OracleIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;

  @Test
  void respondentGetsSeedValidationsPlusLegacySkips() throws Exception {
    mockMvc
        .perform(get("/api/v1/survey/rules").cookie(session(SessionPrincipal.respondent("1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.encuesta").value("EDIN"))
        .andExpect(jsonPath("$.validaciones.B06.min").value(15))
        .andExpect(
            jsonPath("$.saltos[?(@.id == 'legacy-C01A')].entonces.preguntas[0]").value("C01B"));
  }

  @Test
  void respondentOnlyEndpointsRejectQuestionnaireAdministrators() throws Exception {
    Cookie admin =
        session(SessionPrincipal.administrator("adm", EnumSet.of(Role.ADMIN_CUESTIONARIO), null));
    mockMvc.perform(get("/api/v1/survey/rules").cookie(admin)).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/survey/progress").cookie(admin)).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/survey/pages/B1").cookie(admin)).andExpect(status().isForbidden());
  }

  private Cookie session(SessionPrincipal principal) {
    return new Cookie(SessionTokenService.SESSION_COOKIE_NAME, tokens.issueToken(principal));
  }
}
