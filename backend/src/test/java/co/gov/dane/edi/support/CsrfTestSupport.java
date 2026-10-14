package co.gov.dane.edi.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Flujo CSRF real (GET del token, cookie y cabecera). Se evita csrf() de spring-security-test
 * porque deja un repositorio de prueba en el CsrfFilter compartido entre clases de prueba.
 */
public final class CsrfTestSupport {

  private static final String CSRF_ENDPOINT = "/api/v1/auth/csrf";
  private static final String COOKIE_NAME = "XSRF-TOKEN";
  private static final String HEADER_NAME = "X-XSRF-TOKEN";

  private CsrfTestSupport() {}

  public static RequestPostProcessor realCsrf(MockMvc mockMvc) {
    try {
      Cookie cookie =
          mockMvc.perform(get(CSRF_ENDPOINT)).andReturn().getResponse().getCookie(COOKIE_NAME);
      if (cookie == null) {
        throw new IllegalStateException("El endpoint CSRF no emitió la cookie " + COOKIE_NAME);
      }
      return request -> {
        request.setCookies(appendCookie(request.getCookies(), cookie));
        request.addHeader(HEADER_NAME, cookie.getValue());
        return request;
      };
    } catch (Exception exception) {
      throw new IllegalStateException("No se pudo obtener el token CSRF", exception);
    }
  }

  private static Cookie[] appendCookie(Cookie[] existing, Cookie added) {
    if (existing == null) {
      return new Cookie[] {added};
    }
    Cookie[] merged = java.util.Arrays.copyOf(existing, existing.length + 1);
    merged[existing.length] = added;
    return merged;
  }
}
