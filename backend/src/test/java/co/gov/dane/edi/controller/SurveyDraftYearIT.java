package co.gov.dane.edi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

class SurveyDraftYearIT extends OracleIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private SurveyProperties survey;
  @Autowired private JsonMapper jsonMapper;

  private Path file;
  private byte[] original;

  @BeforeEach
  void markActiveYearAsDraft() throws Exception {
    RuleSet published = rulesFiles.load(survey.code(), survey.year());
    file = Path.of(survey.rulesDir()).resolve(survey.code() + "-" + survey.year() + ".json");
    original = Files.readAllBytes(file);
    jsonMapper.writeValue(
        file.toFile(), published.withState(co.gov.dane.edi.admin.YearState.BORRADOR));
    rulesFiles.evict();
  }

  @AfterEach
  void restorePublishedYear() throws Exception {
    Files.write(file, original);
    rulesFiles.evict();
  }

  @Test
  void draftActiveYearMakesRespondentApi503() throws Exception {
    Cookie respondent = session(SessionPrincipal.respondent("1000000001"));
    for (String path :
        new String[] {
          "/api/v1/survey/rules",
          "/api/v1/survey/progress",
          "/api/v1/survey/pages/B1",
          "/api/v1/survey/chapters/B"
        }) {
      mockMvc
          .perform(get(path).cookie(respondent))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.detail").value("La encuesta aún no está disponible."));
    }
  }

  @Test
  void questionnaireAdminStillPreviewsTheDraft() throws Exception {
    Cookie admin =
        session(SessionPrincipal.administrator("adm", EnumSet.of(Role.ADMIN_CUESTIONARIO), null));
    mockMvc.perform(get("/api/v1/survey/chapters/B").cookie(admin)).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/admin/questionnaire/EDIN/2026").cookie(admin))
        .andExpect(jsonPath("$.state").value("borrador"));
  }

  private Cookie session(SessionPrincipal principal) {
    return new Cookie(SessionTokenService.SESSION_COOKIE_NAME, tokens.issueToken(principal));
  }
}
