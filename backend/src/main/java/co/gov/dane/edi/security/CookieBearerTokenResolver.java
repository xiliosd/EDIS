package co.gov.dane.edi.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

/**
 * Lee el JWT desde la cookie HttpOnly. En rutas públicas lo ignora: una cookie vencida no debe
 * impedir volver a iniciar sesión.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

  private final RequestMatcher publicPaths;

  public CookieBearerTokenResolver(RequestMatcher publicPaths) {
    this.publicPaths = publicPaths;
  }

  @Override
  public String resolve(HttpServletRequest request) {
    if (publicPaths.matches(request) || request.getCookies() == null) {
      return null;
    }
    for (Cookie cookie : request.getCookies()) {
      if (SessionTokenService.SESSION_COOKIE_NAME.equals(cookie.getName())
          && StringUtils.hasText(cookie.getValue())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
