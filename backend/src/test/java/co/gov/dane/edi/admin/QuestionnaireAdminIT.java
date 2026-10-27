package co.gov.dane.edi.admin;

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

class QuestionnaireAdminIT extends OracleIntegrationTest {

  private static final String TREE = "/api/v1/admin/questionnaire/EDIN/2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;

  @Test
  void adminTreeListsPagesEntriesAndColumnStatus() throws Exception {
    mockMvc
        .perform(get(TREE).cookie(questionnaireAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.survey").value("EDIN"))
        .andExpect(jsonPath("$.year").value(2026))
        .andExpect(jsonPath("$.state").value("publicado"))
        .andExpect(jsonPath("$.tables.B").value("EDIN_20_CAP_B_UBICA"))
        .andExpect(jsonPath("$.pages.length()").value(9))
        .andExpect(jsonPath("$.pages[0].pageCode").value("B1"))
        .andExpect(jsonPath("$.pages[0].tableName").value("EDIN_20_CAP_B_UBICA"))
        .andExpect(jsonPath("$.pages[0].entries[0].help.codAyu").value(1))
        .andExpect(
            jsonPath("$.pages[0].entries[0].help.text").value("Ayuda ficticia de la página B1"))
        .andExpect(jsonPath("$.pages[0].entries[0].headers.length()").value(2))
        .andExpect(
            jsonPath("$.pages[0].entries[0].headers[?(@.codEnc == 1)].kind").value("HEADING"))
        .andExpect(
            jsonPath("$.pages[0].entries[0].headers[?(@.codEnc == 1)].text")
                .value("Ubicación laboral"))
        .andExpect(jsonPath("$.pages[0].entries[1].help").doesNotExist())
        .andExpect(
            jsonPath("$.pages[0].entries[?(@.columnCode == 'B99')].columnStatus")
                .value("PENDING_COLUMN"))
        .andExpect(
            jsonPath(
                    "$.pages[?(@.pageCode == 'C1')].entries[?(@.columnCode == 'C01A')]"
                        + ".referencedByRules[0]")
                .value("legacy-C01A"));
  }

  @Test
  void textIsStoredPlainAndRenderedEscaped() throws Exception {
    mockMvc
        .perform(get(TREE).cookie(questionnaireAdmin()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.pages[?(@.pageCode == 'C1')].entries[?(@.columnCode == 'C01A')].text")
                .value("¿Pregunta ficticia C01A?"));
  }

  @Test
  void yearsListMarksTheActiveYear() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/years/EDIN").cookie(questionnaireAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.year == 2026)].active").value(true))
        .andExpect(jsonPath("$[?(@.year == 2026)].state").value("publicado"));
  }

  @Test
  void respondentAndOtherAdminsGet403() throws Exception {
    Cookie respondent = session(SessionPrincipal.respondent("1000000001"));
    Cookie territorial =
        session(
            SessionPrincipal.administrator("adm.bogota", EnumSet.of(Role.ADMIN_TERRITORIAL), "11"));
    Cookie national =
        session(SessionPrincipal.administrator("adm.nac", EnumSet.of(Role.ADMIN_NACIONAL), null));
    for (Cookie cookie : new Cookie[] {respondent, territorial, national}) {
      mockMvc.perform(get(TREE).cookie(cookie)).andExpect(status().isForbidden());
      mockMvc
          .perform(get("/api/v1/admin/years/EDIN").cookie(cookie))
          .andExpect(status().isForbidden());
    }
    mockMvc.perform(get(TREE)).andExpect(status().isUnauthorized());
  }

  @Test
  void unknownSurveyIs404() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/questionnaire/XXXX/2026").cookie(questionnaireAdmin()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/admin/years/XXXX").cookie(questionnaireAdmin()))
        .andExpect(status().isNotFound());
  }

  @Test
  void yearWithoutFileOrOutOfRangeIs404() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/questionnaire/EDIN/2031").cookie(questionnaireAdmin()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/admin/questionnaire/EDIN/12").cookie(questionnaireAdmin()))
        .andExpect(status().isNotFound());
  }

  private Cookie questionnaireAdmin() {
    return session(
        SessionPrincipal.administrator("adm.cuest", EnumSet.of(Role.ADMIN_CUESTIONARIO), null));
  }

  private Cookie session(SessionPrincipal principal) {
    return new Cookie(SessionTokenService.SESSION_COOKIE_NAME, tokens.issueToken(principal));
  }
}
