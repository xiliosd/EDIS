package co.gov.dane.edi.controller;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class SurveyAnswerIT extends OracleIntegrationTest {

  private static final String CODE = "9000000901";

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
  void savesTheFirstPageAndUnlocksTheNext() throws Exception {
    save("B1", "{\"answers\":{\"B02\":\"1\",\"B06\":\"30\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextPageCode").value("C1"));
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT B02, B06, ANIO FROM EDIN_20_CAP_B_UBICA WHERE C_C = :cc", Map.of("cc", CODE));
    assertThat(row)
        .containsEntry("B02", "1")
        .containsEntry("B06", "30")
        .containsEntry("ANIO", "2026");
  }

  @Test
  void savedPageCannotBeWrittenAgainAndLockedPageCannotBeWrittenYet() throws Exception {
    save("C2", "{\"answers\":{\"C13\":\"1\"}}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Complete primero las páginas anteriores."));
    save("B1", "{\"answers\":{\"B02\":\"1\",\"B06\":\"30\"}}").andExpect(status().isOk());
    save("B1", "{\"answers\":{\"B02\":\"2\",\"B06\":\"40\"}}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Esta página ya fue guardada."));
  }

  @Test
  void invalidAnswersReturnFieldErrorsInSpanish() throws Exception {
    save("B1", "{\"answers\":{\"B02\":\"\",\"B06\":\"14\"}}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.B02").value("Responda esta pregunta."))
        .andExpect(jsonPath("$.errors.B06").value("El valor está fuera del rango permitido."));
    save("B1", "{\"answers\":{\"B02\":\"7\",\"B06\":\"30\"}}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.B02").value("Seleccione una de las opciones."));
  }

  @Test
  void valueLongerThanColumnIsRejected() throws Exception {
    insertDirect("EDIN_20_CAP_B_UBICA", "B02, B06", "'1', '30'");
    insertDirect("EDIN_20_CAP_C_EXP_PERS", "C01A, C01B, C13", "'1', '1', '1'");
    save("D1", "{\"answers\":{\"D01A\":\"1\",\"D05\":\"123\"}}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.D05").value("La respuesta es demasiado larga."));
  }

  @Test
  void tamperedAnswersAreNeutralizedOrRejected() throws Exception {
    insertDirect("EDIN_20_CAP_B_UBICA", "B02, B06", "'1', '30'");
    save("C1", "{\"answers\":{\"C01A\":\"1\",\"C13\":\"1\",\"ZZ99\":\"1\"}}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.C13").value("Pregunta desconocida."))
        .andExpect(jsonPath("$.errors.ZZ99").value("Pregunta desconocida."));
    // C01A = 2 deshabilita C01B (salto heredado): aunque se envíe "1", se guarda "0" (D15).
    save("C1", "{\"answers\":{\"C01A\":\"2\",\"C01B\":\"1\"}}").andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT C01B FROM EDIN_20_CAP_C_EXP_PERS WHERE C_C = :cc",
                Map.of("cc", CODE),
                String.class))
        .isEqualTo("0");
  }

  @Test
  void concurrentSavesOfSamePageWriteOnce() throws Exception {
    List<Callable<Integer>> attempts = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      attempts.add(
          () ->
              save("B1", "{\"answers\":{\"B02\":\"1\",\"B06\":\"30\"}}")
                  .andReturn()
                  .getResponse()
                  .getStatus());
    }
    ExecutorService pool = Executors.newFixedThreadPool(4);
    List<Integer> statuses = new ArrayList<>();
    for (Future<Integer> result : pool.invokeAll(attempts)) {
      statuses.add(result.get());
    }
    pool.shutdown();
    assertThat(statuses).containsOnlyOnce(200).containsOnly(200, 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM EDIN_20_CAP_B_UBICA WHERE C_C = :cc",
                Map.of("cc", CODE),
                Integer.class))
        .isEqualTo(1);
  }

  private ResultActions save(String page, String body) throws Exception {
    return mockMvc.perform(
        put("/api/v1/survey/pages/" + page + "/answers")
            .with(realCsrf(mockMvc))
            .cookie(
                new Cookie(
                    SessionTokenService.SESSION_COOKIE_NAME,
                    tokens.issueToken(SessionPrincipal.respondent(CODE))))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private void insertDirect(String table, String columns, String values) {
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
}
