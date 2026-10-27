package co.gov.dane.edi.config;

import co.gov.dane.edi.admin.YearCatalogService;
import co.gov.dane.edi.security.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Compuerta única de la API del encuestado: con el año activo en borrador responde 503. El
 * administrador del cuestionario queda exento para poder previsualizar el borrador.
 */
public class SurveyAvailabilityInterceptor implements HandlerInterceptor {

  private final YearCatalogService years;

  public SurveyAvailabilityInterceptor(YearCatalogService years) {
    this.years = years;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!isQuestionnaireAdministrator()) {
      years.requireActiveYearAvailable();
    }
    return true;
  }

  private static boolean isQuestionnaireAdministrator() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(a -> Role.ADMIN_CUESTIONARIO.authority().equals(a.getAuthority()));
  }
}
