package co.gov.dane.edi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.support.OracleIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Exporta el contrato que consume el front; CI falla si el archivo commiteado queda desfasado. */
class OpenApiExportIT extends OracleIntegrationTest {

  private static final Path CONTRACT = Path.of("..", "docs", "api", "openapi.yaml");

  @Autowired private MockMvc mockMvc;

  @Test
  void exportsTheContractWithAuthenticationEndpoints() throws Exception {
    String yaml =
        mockMvc
            .perform(get("/v3/api-docs.yaml"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains("/api/v1/auth/login")
        .contains("/api/v1/auth/me")
        .contains("/api/v1/survey/chapters")
        .contains("sessionCookie")
        .doesNotContain("/probe")
        .doesNotContain("/test/")
        .doesNotContain("csrfToken")
        .doesNotContain("CsrfToken:")
        .doesNotContain("localhost")
        .contains("application/problem+json");

    Files.createDirectories(CONTRACT.getParent());
    Files.writeString(CONTRACT, yaml, StandardCharsets.UTF_8);
  }

  @Test
  void swaggerUiIsAvailableOutsideProduction() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
