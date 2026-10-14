package co.gov.dane.edi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** En producción la documentación solo la ve el administrador nacional. */
@TestPropertySource(properties = "app.security.swagger-public=false")
class OpenApiProtectedIT extends OracleIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;

  @Test
  void anonymousUserCannotReadTheContract() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
  }

  @Test
  void respondentCannotReadTheContract() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs").cookie(session(SessionPrincipal.respondent("1"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void nationalAdministratorCanReadTheContract() throws Exception {
    SessionPrincipal admin =
        SessionPrincipal.administrator("adm.nacional", EnumSet.of(Role.ADMIN_NACIONAL), null);
    mockMvc.perform(get("/v3/api-docs").cookie(session(admin))).andExpect(status().isOk());
  }

  private Cookie session(SessionPrincipal principal) {
    return new Cookie(SessionTokenService.SESSION_COOKIE_NAME, tokens.issueToken(principal));
  }
}
