package co.gov.dane.edi.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Ayudas que el encuestado ve en el detalle de página (FRM_PRE_AYUDA / FRM_ENC_AYUDA). */
class SurveyPageHelpIT extends OracleIntegrationTest {

  private static final String PAGE = "/api/v1/survey/pages/B1";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private NamedParameterJdbcTemplate jdbc;
  @Autowired private QuestionnaireService questionnaire;

  /**
   * Ayudas extra solo para esta prueba sobre la pregunta 103 ("Edad en años cumplidos"): una con
   * definición de varias líneas y entidades, una sin definición en GRAL_AYUDAS, una sin palabra y
   * otra que reutiliza la definición 501, para ver el orden por COD_PRE_AYU.
   */
  @BeforeEach
  void addHelp() {
    jdbc.update(
        "INSERT INTO GRAL_AYUDAS (PROY, COD, TITULO, DESCRIPCION, ANIO) VALUES ('EDIN', 990,"
            + " 'Edad &#8220;cumplida&#8221;',"
            + " '<p>Primera l&iacute;nea<br>Segunda <b>l&iacute;nea</b></p>"
            + "<p>Otro p&aacute;rrafo</p>',"
            + " 2026)",
        Map.of());
    jdbc.update(
        "INSERT INTO FRM_PRE_AYUDA (COD_PRE_AYU, COD_CAP_PROY, COD_AYU, TEXTO) VALUES"
            + " (990, 103, 990, 'a&ntilde;os cumplidos'), (991, 103, 9999, 'Edad'),"
            + " (992, 103, 501, ' <b></b> '), (993, 103, 501, 'Edad')",
        Map.of());
    questionnaire.evict();
  }

  @AfterEach
  void removeHelp() {
    jdbc.update("DELETE FROM FRM_PRE_AYUDA WHERE COD_PRE_AYU BETWEEN 990 AND 993", Map.of());
    jdbc.update("DELETE FROM GRAL_AYUDAS WHERE COD = 990", Map.of());
    questionnaire.evict();
  }

  @Test
  void questionHelpComesAsPlainTextInOrderWithoutUnusableEntries() throws Exception {
    mockMvc
        .perform(get(PAGE).cookie(respondent()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries[2].text").value("Edad en años cumplidos"))
        .andExpect(jsonPath("$.entries[2].help.length()").value(2))
        .andExpect(jsonPath("$.entries[2].help[0].term").value("años cumplidos"))
        .andExpect(jsonPath("$.entries[2].help[0].title").value("Edad “cumplida”"))
        .andExpect(
            jsonPath("$.entries[2].help[0].definition")
                .value("Primera línea\nSegunda línea\n\nOtro párrafo"))
        .andExpect(jsonPath("$.entries[2].help[1].term").value("Edad"))
        .andExpect(jsonPath("$.entries[2].help[1].title").value("Desempeño"))
        .andExpect(content().string(not(containsString("<b>"))))
        .andExpect(content().string(not(containsString("&oacute;"))));
  }

  @Test
  void statementAndHeaderCarryTheirSeededHelp() throws Exception {
    mockMvc
        .perform(get(PAGE).cookie(respondent()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries[0].kind").value("STATEMENT"))
        .andExpect(jsonPath("$.entries[0].help[0].term").value("desempeña"))
        .andExpect(jsonPath("$.entries[0].help[0].title").value("Desempeño"))
        .andExpect(
            jsonPath("$.entries[0].help[0].definition").value("Definición ficticia de desempeño."))
        .andExpect(jsonPath("$.entries[0].headers[0].kind").value("SUBTITLE"))
        .andExpect(jsonPath("$.entries[0].headers[0].help.length()").value(0))
        .andExpect(jsonPath("$.entries[0].headers[1].kind").value("HEADING"))
        .andExpect(jsonPath("$.entries[0].headers[1].help[0].term").value("laboral"))
        .andExpect(jsonPath("$.entries[0].headers[1].help[0].title").value("Ubicación laboral"))
        .andExpect(
            jsonPath("$.entries[0].headers[1].help[0].definition")
                .value("Definición ficticia del lugar de trabajo."))
        .andExpect(jsonPath("$.entries[1].help.length()").value(0));
  }

  private Cookie respondent() {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(SessionPrincipal.respondent("1000000001")));
  }
}
