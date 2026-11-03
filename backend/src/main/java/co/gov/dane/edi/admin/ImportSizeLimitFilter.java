package co.gov.dane.edi.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tope de 1 MB para el cuerpo de la importación de reglas: un archivo de reglas real pesa unos
 * pocos KB, y así un cuerpo enorme no llega a deserializarse. Se revisa el Content-Length antes de
 * leer el cuerpo.
 */
@Component
public class ImportSizeLimitFilter extends OncePerRequestFilter {

  static final long MAX_BYTES = 1_048_576;

  private static final String BODY =
      "{\"type\":\"about:blank\",\"title\":\"Archivo demasiado grande\",\"status\":413,"
          + "\"detail\":\"El archivo supera 1 MB.\"}";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !HttpMethod.PUT.matches(request.getMethod())
        || !path.startsWith("/api/v1/admin/questionnaire/")
        || !path.endsWith("/rules/import");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BYTES) {
      response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write(BODY);
      return;
    }
    chain.doFilter(request, response);
  }
}
