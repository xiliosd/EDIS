package co.gov.dane.edi.security;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.support.OracleIntegrationTest;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** RemoteIpValve confía en el loopback: X-Forwarded-For define la IP del bloqueo (spec §7.2). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForwardedClientIpIT extends OracleIntegrationTest {

  // Usuario exclusivo de esta prueba: el bloqueo vive en memoria y se comparte entre pruebas.
  private static final String USERNAME = "IPPRUEBA";

  @LocalServerPort private int port;

  @Test
  void lockoutIsPerForwardedClientAddress() throws Exception {
    CookieManager cookies = new CookieManager();
    HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
    client.send(
        HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),
        HttpResponse.BodyHandlers.discarding());
    String csrf =
        cookies.getCookieStore().getCookies().stream()
            .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
            .map(HttpCookie::getValue)
            .findFirst()
            .orElseThrow();

    for (int attempt = 0; attempt < 5; attempt++) {
      assertThat(login(client, csrf, "203.0.113.10")).isEqualTo(401);
    }
    assertThat(login(client, csrf, "203.0.113.10")).isEqualTo(429);
    assertThat(login(client, csrf, "203.0.113.20")).isEqualTo(401);
  }

  private int login(HttpClient client, String csrf, String forwardedFor) throws Exception {
    String body =
        "{\"accountType\":\"RESPONDENT\",\"username\":\"" + USERNAME + "\",\"password\":\"x\"}";
    HttpRequest request =
        HttpRequest.newBuilder(uri("/api/v1/auth/login"))
            .header("Content-Type", "application/json")
            .header("X-XSRF-TOKEN", csrf)
            .header("X-Forwarded-For", forwardedFor)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
