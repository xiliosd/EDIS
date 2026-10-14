package co.gov.dane.edi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// csrf() deja un repositorio de tokens de prueba en el CsrfFilter compartido; sin recargar el
// contexto contaminaría a SecurityConfigIT (cookie XSRF-TOKEN ausente).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerIT extends OracleIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void respondentLogsInAndGetsHttpOnlySessionCookie() throws Exception {
    login("RESPONDENT", "PRUEBA01", "Clave#01")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountType").value("RESPONDENT"))
        .andExpect(jsonPath("$.roles[0]").value("ENCUESTADO"))
        .andExpect(jsonPath("$.surveyCode").value("EDIN"))
        .andExpect(jsonPath("$.surveyYear").value(2026))
        .andExpect(cookie().httpOnly(SessionTokenService.SESSION_COOKIE_NAME, true))
        .andExpect(cookie().sameSite(SessionTokenService.SESSION_COOKIE_NAME, "Strict"));
  }

  @Test
  void loginResponseAndLogsDoNotExposePersonalData(CapturedOutput output) throws Exception {
    MvcResult result = login("RESPONDENT", "PRUEBA01", "Clave#01").andReturn();
    String body = result.getResponse().getContentAsString();
    assertThat(body).doesNotContain("1000000001").doesNotContain("Persona Ficticia");
    assertThat(output.getAll())
        .doesNotContain("1000000001")
        .doesNotContain("Clave#01")
        .doesNotContain("PRUEBA01");
  }

  @Test
  void meReturnsTheCurrentSession() throws Exception {
    Cookie session = sessionCookieOf(login("RESPONDENT", "PRUEBA01", "Clave#01").andReturn());
    mockMvc
        .perform(get("/api/v1/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("ENCUESTADO"));
  }

  @Test
  void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
    login("RESPONDENT", "PRUEBA01", "Errada01")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Usuario o clave incorrectos."));
    login("RESPONDENT", "NOEXISTE", "Errada01")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Usuario o clave incorrectos."));
  }

  @Test
  void disabledRespondentCannotLogIn() throws Exception {
    login("RESPONDENT", "PRUEBA02", "Clave#02").andExpect(status().isUnauthorized());
  }

  @Test
  void nationalAdministratorGetsQuestionnaireRole() throws Exception {
    login("ADMINISTRATOR", "adm.nacional", "Nac#2026x")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountType").value("ADMINISTRATOR"))
        .andExpect(jsonPath("$.roles.length()").value(2))
        .andExpect(jsonPath("$.roles[0]").value("ADMIN_CUESTIONARIO"))
        .andExpect(jsonPath("$.roles[1]").value("ADMIN_NACIONAL"));
  }

  @Test
  void territorialAdministratorCarriesDepartment() throws Exception {
    login("ADMINISTRATOR", "adm.bogota", "Bog#2026x")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("ADMIN_TERRITORIAL"))
        .andExpect(jsonPath("$.departmentCode").value("11"));
  }

  @Test
  void lockoutIgnoresUsernameCase() throws Exception {
    String[] variants = {"PRUEBA04", "prueba04", "Prueba04", " PRUEBA04", "pRUEBA04"};
    for (String variant : variants) {
      login("RESPONDENT", variant, "Errada01").andExpect(status().isUnauthorized());
    }
    login("RESPONDENT", "PRUEBA04", "Clave#04")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  void loginWithoutCsrfTokenIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("RESPONDENT", "PRUEBA01", "Clave#01")))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidPayloadListsFieldErrors() throws Exception {
    login("RESPONDENT", "", "Clave#01")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.username").value("Ingrese su usuario"));
  }

  @Test
  void logoutDeletesTheSessionCookie() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/logout").with(csrf()))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge(SessionTokenService.SESSION_COOKIE_NAME, 0));
  }

  private org.springframework.test.web.servlet.ResultActions login(
      String accountType, String username, String password) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/v1/auth/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload(accountType, username, password));
    return mockMvc.perform(request);
  }

  private static String payload(String accountType, String username, String password) {
    return "{\"accountType\":\"%s\",\"username\":\"%s\",\"password\":\"%s\"}"
        .formatted(accountType, username, password);
  }

  private static Cookie sessionCookieOf(MvcResult result) {
    return result.getResponse().getCookie(SessionTokenService.SESSION_COOKIE_NAME);
  }
}
