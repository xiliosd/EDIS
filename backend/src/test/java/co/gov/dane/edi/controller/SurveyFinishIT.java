package co.gov.dane.edi.controller;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class SurveyFinishIT extends OracleIntegrationTest {

  private static final String CODE = "9000001001";

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
  void cannotFinishWithPagesMissing() throws Exception {
    finish("{\"observations\":\"Hola\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Faltan páginas por guardar."));
  }

  @Test
  void finishingStoresObservationsOnceAndReturnsAStableReceipt() throws Exception {
    saveAllPagesDirectly();

    String receipt =
        receiptOf(
            finish("{\"observations\":\"  Gracias por la encuesta\\n \"}")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(receipt).matches("[A-Z2-7]{5}-[A-Z2-7]{5}");
    String repeated =
        receiptOf(
            finish("{\"observations\":\"Otra cosa\"}")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(repeated).isEqualTo(receipt);

    assertThat(
            jdbc.queryForObject(
                "SELECT OBS FROM EDIN_20_CAP_H_PREV_PRACT WHERE C_C = :cc",
                Map.of("cc", CODE),
                String.class))
        .isEqualTo("Gracias por la encuesta");
    assertThat(receipt).doesNotContain(CODE);
    mockMvc
        .perform(get("/api/v1/survey/progress").cookie(session()))
        .andExpect(jsonPath("$.finished").value(true))
        .andExpect(jsonPath("$.observationsSaved").value(true))
        .andExpect(jsonPath("$.receipt").isNotEmpty());
  }

  @Test
  void blankObservationsAreAcceptedAndLeaveObsNull() throws Exception {
    saveAllPagesDirectly();
    finish("{\"observations\":\"   \\t \"}").andExpect(status().isOk());
    assertThat(storedObservations()).isNull();
  }

  @Test
  void observationsThatDoNotFitInBytesAreRejectedWith422() throws Exception {
    saveAllPagesDirectly();
    finish("{\"observations\":\"" + "ñ".repeat(700) + "\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.errors.observations")
                .value("Las observaciones superan el espacio disponible. Acórtelas."));
    assertThat(storedObservations()).isNull();
  }

  @Test
  void observationsLongerThan1100CharactersAreRejected() throws Exception {
    saveAllPagesDirectly();
    finish("{\"observations\":\"" + "a".repeat(1101) + "\"}").andExpect(status().isBadRequest());
  }

  private String storedObservations() {
    return jdbc.queryForObject(
        "SELECT OBS FROM EDIN_20_CAP_H_PREV_PRACT WHERE C_C = :cc",
        Map.of("cc", CODE),
        String.class);
  }

  private static String receiptOf(String json) {
    return JsonPath.read(json, "$.receipt");
  }

  private void saveAllPagesDirectly() {
    insert("EDIN_20_CAP_B_UBICA", "B02, B06", "'1', '30'");
    insert("EDIN_20_CAP_C_EXP_PERS", "C01A, C01B, C13", "'1', '1', '1'");
    insert("EDIN_20_CAP_D_GEST_TAL", "D01A, D05, D09", "'1', '5', '1'");
    insert("EDIN_20_CAP_E_USO_TECN", "E01A", "'1'");
    insert("EDIN_20_CAP_F_GEST_ENT", "F01A", "'1'");
    insert("EDIN_20_CAP_G_REN_CUEN", "G01A", "'1'");
    insert("EDIN_20_CAP_H_PREV_PRACT", "H01", "'1'");
  }

  private void insert(String table, String columns, String values) {
    jdbc.update(
        "INSERT INTO "
            + table
            + " (C_C, ESTADO, TC, "
            + columns
            + ") VALUES (:cc, 1, 1, "
            + values
            + ")",
        Map.of("cc", CODE));
  }

  private ResultActions finish(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/survey/finish")
            .with(realCsrf(mockMvc))
            .cookie(session())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private Cookie session() {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(SessionPrincipal.respondent(CODE)));
  }
}
