package co.gov.dane.edi.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class SessionTokenService {

  public static final String SESSION_COOKIE_NAME = "EDI_SESSION";
  public static final String ISSUER = "formulario-edi";
  public static final String ROLES_CLAIM = "roles";
  public static final String DEPARTMENT_CLAIM = "dpto";

  private final JwtEncoder encoder;
  private final Clock clock;
  private final AppSecurityProperties properties;
  private final SubjectCipher subjectCipher;

  public SessionTokenService(
      JwtEncoder encoder,
      Clock clock,
      AppSecurityProperties properties,
      SubjectCipher subjectCipher) {
    this.encoder = encoder;
    this.clock = clock;
    this.properties = properties;
    this.subjectCipher = subjectCipher;
  }

  public String issueToken(SessionPrincipal principal) {
    return issueToken(principal, clock.instant());
  }

  public String issueToken(SessionPrincipal principal, Instant issuedAt) {
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(tokenSubject(principal))
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.sessionDuration()))
            .claim(ROLES_CLAIM, principal.roles().stream().map(Role::name).sorted().toList());
    if (principal.departmentCode() != null) {
      claims.claim(DEPARTMENT_CLAIM, principal.departmentCode());
    }
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }

  public ResponseCookie sessionCookie(String token) {
    return baseCookie(token).maxAge(properties.sessionDuration()).build();
  }

  public ResponseCookie expiredSessionCookie() {
    return baseCookie("").maxAge(Duration.ZERO).build();
  }

  /** Renovación deslizante: el encuestado no pierde la sesión mientras siga respondiendo. */
  public boolean needsRenewal(Jwt jwt) {
    Duration remaining = Duration.between(clock.instant(), jwt.getExpiresAt());
    return remaining.compareTo(properties.sessionDuration().dividedBy(2)) < 0;
  }

  /** Reconstruye la identidad de la sesión, descifrando el C_C del encuestado. */
  public SessionPrincipal principalFrom(Jwt jwt) {
    SessionPrincipal raw = SessionPrincipal.fromJwt(jwt);
    if (!raw.isRespondent()) {
      return raw;
    }
    String code = subjectCipher.decrypt(raw.respondentCode());
    return new SessionPrincipal(
        SessionPrincipal.RESPONDENT_PREFIX + code, raw.roles(), raw.departmentCode());
  }

  private String tokenSubject(SessionPrincipal principal) {
    return principal.isRespondent()
        ? SessionPrincipal.RESPONDENT_PREFIX + subjectCipher.encrypt(principal.respondentCode())
        : principal.subject();
  }

  private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
    return ResponseCookie.from(SESSION_COOKIE_NAME, value)
        .httpOnly(true)
        .secure(properties.cookieSecure())
        .sameSite("Strict")
        .path("/");
  }
}
