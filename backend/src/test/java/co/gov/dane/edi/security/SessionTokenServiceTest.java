package co.gov.dane.edi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class SessionTokenServiceTest {

  private SessionTokenService service;
  private JwtDecoder decoder;

  @BeforeEach
  void setUp() {
    AppSecurityProperties properties =
        new AppSecurityProperties(
            "0123456789abcdef0123456789abcdef", 30, true, "http://localhost", 5, 15, false);
    JwtConfig config = new JwtConfig();
    var key = config.sessionSigningKey(properties);
    // El decodificador valida la expiración con el reloj real, por eso el token se emite "ahora".
    service =
        new SessionTokenService(
            config.jwtEncoder(key), Clock.fixed(Instant.now(), ZoneOffset.UTC), properties);
    decoder = config.jwtDecoder(key);
  }

  @Test
  void issuesSignedTokenWithRolesAndDepartment() {
    SessionPrincipal principal =
        SessionPrincipal.administrator("adm.bogota", EnumSet.of(Role.ADMIN_TERRITORIAL), "11");
    Jwt jwt = decoder.decode(service.issueToken(principal));

    assertThat(jwt.getSubject()).isEqualTo("admin:adm.bogota");
    assertThat(jwt.getClaimAsStringList(SessionTokenService.ROLES_CLAIM))
        .containsExactly("ADMIN_TERRITORIAL");
    assertThat(SessionPrincipal.fromJwt(jwt)).isEqualTo(principal);
  }

  @Test
  void ignoresUnknownRoleNamesInValidlySignedToken() {
    var principal =
        SessionPrincipal.administrator("adm.bogota", EnumSet.of(Role.ADMIN_TERRITORIAL), "11");
    Jwt jwt = decoder.decode(service.issueToken(principal));
    Jwt withUnknownRole =
        Jwt.withTokenValue(jwt.getTokenValue())
            .headers(headers -> headers.putAll(jwt.getHeaders()))
            .claims(claims -> claims.putAll(jwt.getClaims()))
            .claim(SessionTokenService.ROLES_CLAIM, List.of("ADMIN_TERRITORIAL", "FANTASMA"))
            .build();

    assertThat(SessionPrincipal.fromJwt(withUnknownRole).roles())
        .containsExactly(Role.ADMIN_TERRITORIAL);
  }

  @Test
  void tokenExpiresAfterSessionDuration() {
    Jwt jwt = decoder.decode(service.issueToken(SessionPrincipal.respondent("1000000001")));
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void sessionCookieIsHttpOnlyStrictAndSecure() {
    ResponseCookie cookie = service.sessionCookie("valor");
    assertThat(cookie.getName()).isEqualTo("EDI_SESSION");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void expiredCookieDeletesTheSession() {
    ResponseCookie cookie = service.expiredSessionCookie();
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    assertThat(cookie.getValue()).isEmpty();
  }

  @Test
  void renewsOnlyWhenLessThanHalfOfTheSessionRemains() {
    Instant now = Instant.now();
    Jwt fresh = decoder.decode(service.issueToken(SessionPrincipal.respondent("1"), now));
    Jwt old =
        decoder.decode(
            service.issueToken(SessionPrincipal.respondent("1"), now.minusSeconds(20 * 60)));
    assertThat(service.needsRenewal(fresh)).isFalse();
    assertThat(service.needsRenewal(old)).isTrue();
  }

  @Test
  void respondentPrincipalIsRecognized() {
    assertThat(SessionPrincipal.respondent("1").isRespondent()).isTrue();
    assertThat(
            SessionPrincipal.administrator("a", EnumSet.of(Role.ADMIN_NACIONAL), null)
                .isRespondent())
        .isFalse();
  }
}
