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

class SurveyControllerIT extends OracleIntegrationTest {

  private static final String EXPECTED_STATEMENT =
      "Nivel del empleo que desempeña en la entidad " + "durante el 2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;

  @Test
  void respondentListsChapters() throws Exception {
    mockMvc
        .perform(get("/api/v1/survey/chapters").cookie(respondent()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(7))
        .andExpect(jsonPath("$[0].code").value("B"))
        .andExpect(jsonPath("$[0].title").value("Ubicación laboral"));
  }

  @Test
  void respondentReadsChapterWithoutPendingQuestions() throws Exception {
    mockMvc
        .perform(get("/api/v1/survey/chapters/B").cookie(respondent()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(3))
        .andExpect(jsonPath("$.entries[0].kind").value("STATEMENT"))
        .andExpect(jsonPath("$.entries[0].text").value(EXPECTED_STATEMENT))
        .andExpect(jsonPath("$.entries[1].answer.options[1].label").value("Técnico"));
  }

  @Test
  void questionnaireAdministratorSeesPendingColumn() throws Exception {
    Cookie admin =
        session(
            SessionPrincipal.administrator(
                "adm.nacional", EnumSet.of(Role.ADMIN_NACIONAL, Role.ADMIN_CUESTIONARIO), null));
    mockMvc
        .perform(get("/api/v1/survey/chapters/B").cookie(admin))
        .andExpect(jsonPath("$.entries.length()").value(4))
        .andExpect(jsonPath("$.entries[3].answer.columnStatus").value("PENDING_COLUMN"));
  }

  @Test
  void unknownChapterReturns404() throws Exception {
    mockMvc
        .perform(get("/api/v1/survey/chapters/Z").cookie(respondent()))
        .andExpect(status().isNotFound());
  }

  @Test
  void territorialAdministratorCannotReadTheSurvey() throws Exception {
    Cookie territorial =
        session(
            SessionPrincipal.administrator("adm.bogota", EnumSet.of(Role.ADMIN_TERRITORIAL), "11"));
    mockMvc
        .perform(get("/api/v1/survey/chapters").cookie(territorial))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousUserIsRejected() throws Exception {
    mockMvc.perform(get("/api/v1/survey/chapters")).andExpect(status().isUnauthorized());
  }

  private Cookie respondent() {
    return session(SessionPrincipal.respondent("1000000001"));
  }

  private Cookie session(SessionPrincipal principal) {
    return new Cookie(SessionTokenService.SESSION_COOKIE_NAME, tokens.issueToken(principal));
  }
}
