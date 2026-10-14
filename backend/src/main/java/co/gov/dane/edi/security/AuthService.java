package co.gov.dane.edi.security;

import co.gov.dane.edi.model.AccountType;
import co.gov.dane.edi.model.LoginRequest;
import co.gov.dane.edi.repository.AdminAccountRepository;
import co.gov.dane.edi.repository.RespondentAccountRepository;
import co.gov.dane.edi.service.error.InvalidCredentialsException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

  private final RespondentAccountRepository respondents;
  private final AdminAccountRepository admins;
  private final LoginAttemptService loginAttempts;

  public AuthService(
      RespondentAccountRepository respondents,
      AdminAccountRepository admins,
      LoginAttemptService loginAttempts) {
    this.respondents = respondents;
    this.admins = admins;
    this.loginAttempts = loginAttempts;
  }

  public SessionPrincipal authenticate(LoginRequest request, String clientAddress) {
    String attemptKey = LoginAttemptService.keyFor(request.username(), clientAddress);
    loginAttempts.ensureNotLocked(attemptKey);
    Optional<SessionPrincipal> principal =
        request.accountType() == AccountType.RESPONDENT
            ? authenticateRespondent(request)
            : authenticateAdministrator(request);
    if (principal.isEmpty()) {
      loginAttempts.recordFailure(attemptKey);
      // Sin usuario ni IP en el log: son datos personales (Ley 1581).
      LOGGER.warn("Inicio de sesión rechazado tipo={}", request.accountType());
      throw new InvalidCredentialsException();
    }
    loginAttempts.recordSuccess(attemptKey);
    LOGGER.info("Inicio de sesión exitoso tipo={}", request.accountType());
    return principal.get();
  }

  /** Traducción de GRAL_USUARIOS_ADM.ROL del sistema heredado (admin/Menu.php). */
  public static Set<Role> rolesForLegacyAdminRole(int legacyRole) {
    return switch (legacyRole) {
      case 1 -> EnumSet.of(Role.ADMIN_NACIONAL, Role.ADMIN_CUESTIONARIO);
      case 2 -> EnumSet.of(Role.ADMIN_TERRITORIAL);
      case 3, 4 -> EnumSet.of(Role.ADMIN_OPERATIVO);
      default -> EnumSet.noneOf(Role.class);
    };
  }

  /**
   * Las claves heredadas están en texto plano (decisión D7, deuda técnica). Se comparan en tiempo
   * constante para no filtrar por tiempo de respuesta cuántos caracteres coinciden.
   */
  public static boolean passwordMatches(String stored, String provided) {
    byte[] expected = (stored == null ? "" : stored).getBytes(StandardCharsets.UTF_8);
    byte[] actual = provided.getBytes(StandardCharsets.UTF_8);
    boolean equal = MessageDigest.isEqual(expected, actual);
    return stored != null && equal;
  }

  private Optional<SessionPrincipal> authenticateRespondent(LoginRequest request) {
    return respondents
        .findByLogin(request.username().strip())
        .filter(account -> passwordMatches(account.storedPassword(), request.password()))
        .filter(account -> account.enabledForWeb())
        .map(account -> SessionPrincipal.respondent(account.respondentCode()));
  }

  private Optional<SessionPrincipal> authenticateAdministrator(LoginRequest request) {
    return admins
        .findByUsername(request.username().strip())
        .filter(account -> passwordMatches(account.storedPassword(), request.password()))
        .flatMap(
            account -> {
              Set<Role> roles = rolesForLegacyAdminRole(account.legacyRole());
              return roles.isEmpty()
                  ? Optional.empty()
                  : Optional.of(
                      SessionPrincipal.administrator(
                          account.username(), roles, account.departmentCode()));
            });
  }
}
