package co.gov.dane.edi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.admin.YearState;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.NewColumn;
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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

class SurveyRulesIT extends OracleIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private EffectiveRulesService effectiveRules;
  @Autowired private SurveyProperties survey;
  @Autowired private JsonMapper jsonMapper;

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
  void publishedYearExposesNeitherStateNorTables() throws Exception {
    mockMvc
        .perform(get("/api/v1/survey/rules").cookie(session(SessionPrincipal.respondent("1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").doesNotExist())
        .andExpect(jsonPath("$.tablas").doesNotExist());
  }

  @Test
  void respondentNeverSeesStateTablesOrNewColumnsEvenWhenTheFileHasThem() throws Exception {
    RuleSet published = rulesFiles.load(survey.code(), survey.year());
    Path file = Path.of(survey.rulesDir()).resolve(survey.code() + "-" + survey.year() + ".json");
    byte[] original = Files.readAllBytes(file);
    try {
      jsonMapper.writeValue(
          file.toFile(),
          published
              .withState(YearState.PUBLICADO)
              .withTables(Map.of("B", Chapter.B.tableName()))
              .withNewColumns(Map.of("B99", new NewColumn("VARCHAR2", 10))));
      rulesFiles.evict();
      effectiveRules.evict();
      mockMvc
          .perform(get("/api/v1/survey/rules").cookie(session(SessionPrincipal.respondent("1"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.encuesta").value("EDIN"))
          .andExpect(jsonPath("$.validaciones.B06.min").value(15))
          .andExpect(jsonPath("$.estado").doesNotExist())
          .andExpect(jsonPath("$.tablas").doesNotExist())
          .andExpect(jsonPath("$.columnasNuevas").doesNotExist());
    } finally {
      Files.write(file, original);
      rulesFiles.evict();
      effectiveRules.evict();
    }
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
