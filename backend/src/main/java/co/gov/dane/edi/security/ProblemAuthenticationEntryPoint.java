package co.gov.dane.edi.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final SessionTokenService tokens;

  public ProblemAuthenticationEntryPoint(SessionTokenService tokens) {
    this.tokens = tokens;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    // Borra una cookie inválida o vencida para que el navegador no la siga enviando.
    response.addHeader(HttpHeaders.SET_COOKIE, tokens.expiredSessionCookie().toString());
    ProblemResponseWriter.write(
        response, HttpStatus.UNAUTHORIZED, "Sesión requerida", "Inicie sesión para continuar.");
  }
}
