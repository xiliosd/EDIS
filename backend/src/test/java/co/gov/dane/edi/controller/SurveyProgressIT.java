package co.gov.dane.edi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class SurveyProgressIT extends OracleIntegrationTest {

  private static final String CODE = "9000000801";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    for (Chapter chapter : Chapter.values()) {
      jdbc.update("DELETE FROM " + chapter.tableName() + " WHERE C_C = :cc", Map.of("cc", CODE));
    }
  }

  @Test
  void newRespondentCanOnlyStartWithTheFirstPage() throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/survey/progress").cookie(respondent()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pages.length()").value(9))
            .andExpect(jsonPath("$.pages[0].pageCode").value("B1"))
            .andExpect(jsonPath("$.pages[0].state").value("AVAILABLE"))
            .andExpect(jsonPath("$.pages[1].state").value("LOCKED"))
            .andExpect(jsonPath("$.finished").value(false))
            .andExpect(jsonPath("$.receipt").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain(CODE);
  }

  @Test
  void savedFirstPageUnlocksTheNextAndShowsItsAnswers() throws Exception {
    jdbc.update(
        "INSERT INTO EDIN_20_CAP_B_UBICA (C_C, ESTADO, TC, ANIO, B02, B06)"
            + " VALUES (:cc, 1, 1, '2026', '1', '30')",
        Map.of("cc", CODE));

    mockMvc
        .perform(get("/api/v1/survey/progress").cookie(respondent()))
        .andExpect(jsonPath("$.pages[0].state").value("SAVED"))
        .andExpect(jsonPath("$.pages[1].state").value("AVAILABLE"))
        .andExpect(jsonPath("$.pages[2].state").value("LOCKED"));
    mockMvc
        .perform(get("/api/v1/survey/pages/B1").cookie(respondent()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SAVED"))
        .andExpect(jsonPath("$.answers.B02").value("1"))
        .andExpect(jsonPath("$.entries[1].pageCode").value("B1"));
  }

  @Test
  void unsavedPageHasNoAnswersAndUnknownPageIs404() throws Exception {
    mockMvc
        .perform(get("/api/v1/survey/pages/c1").cookie(respondent()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageCode").value("C1"))
        .andExpect(jsonPath("$.state").value("LOCKED"))
        .andExpect(jsonPath("$.answers").isEmpty());
    mockMvc
        .perform(get("/api/v1/survey/pages/Z9").cookie(respondent()))
        .andExpect(status().isNotFound());
  }

  private Cookie respondent() {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(SessionPrincipal.respondent(CODE)));
  }
}
