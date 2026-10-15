package co.gov.dane.edi.security;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/** Identidad de la sesión, reconstruible desde el JWT para no consultar Oracle en cada petición. */
public record SessionPrincipal(String subject, Set<Role> roles, String departmentCode) {

  private static final String RESPONDENT_PREFIX = "encuestado:";
  private static final String ADMIN_PREFIX = "admin:";

  public SessionPrincipal {
    roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
  }

  public static SessionPrincipal respondent(String respondentCode) {
    return new SessionPrincipal(
        RESPONDENT_PREFIX + respondentCode, EnumSet.of(Role.ENCUESTADO), null);
  }

  public static SessionPrincipal administrator(
      String username, Set<Role> roles, String departmentCode) {
    return new SessionPrincipal(ADMIN_PREFIX + username, roles, departmentCode);
  }

  public static SessionPrincipal fromJwt(Jwt jwt) {
    List<String> roleNames = jwt.getClaimAsStringList(SessionTokenService.ROLES_CLAIM);
    Set<Role> roles = EnumSet.noneOf(Role.class);
    if (roleNames != null) {
      // Un rol desconocido (p. ej. de una versión anterior) se ignora en vez de provocar un 500.
      roleNames.forEach(name -> parseRole(name).ifPresent(roles::add));
    }
    return new SessionPrincipal(
        jwt.getSubject(), roles, jwt.getClaimAsString(SessionTokenService.DEPARTMENT_CLAIM));
  }

  private static Optional<Role> parseRole(String name) {
    return Arrays.stream(Role.values()).filter(role -> role.name().equals(name)).findFirst();
  }

  public boolean isRespondent() {
    return subject.startsWith(RESPONDENT_PREFIX);
  }

  @Override
  public String toString() {
    return "SessionPrincipal[roles=" + roles + "]";
  }
}
