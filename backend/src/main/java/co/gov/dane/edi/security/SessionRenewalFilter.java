package co.gov.dane.edi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionRenewalFilter extends OncePerRequestFilter {

  private final SessionTokenService tokens;

  public SessionRenewalFilter(SessionTokenService tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication
        && !request.getRequestURI().endsWith("/auth/logout")
        && tokens.needsRenewal(jwtAuthentication.getToken())) {
      String renewed = tokens.issueToken(SessionPrincipal.fromJwt(jwtAuthentication.getToken()));
      response.addHeader(HttpHeaders.SET_COOKIE, tokens.sessionCookie(renewed).toString());
    }
    chain.doFilter(request, response);
  }
}
