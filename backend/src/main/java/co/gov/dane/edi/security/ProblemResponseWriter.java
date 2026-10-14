package co.gov.dane.edi.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Escribe un problem+json desde los filtros de seguridad, donde no llega @RestControllerAdvice. */
final class ProblemResponseWriter {

  private ProblemResponseWriter() {}

  /** Los textos son constantes del código; no reciben datos del usuario, por eso no se escapan. */
  static void write(HttpServletResponse response, HttpStatus status, String title, String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + title
                + "\",\"status\":"
                + status.value()
                + ",\"detail\":\""
                + detail
                + "\"}");
  }
}
