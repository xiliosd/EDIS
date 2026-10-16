package co.gov.dane.edi.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  static final String[] PUBLIC_PATHS = {
    "/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/csrf", "/actuator/health/**"
  };
  static final String[] DOCUMENTATION_PATHS = {
    "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html"
  };
  // 'unsafe-inline' solo en estilos: Swagger UI lo requiere; el JSON de la API no lo usa.
  private static final String CONTENT_SECURITY_POLICY =
      "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
          + "script-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      SessionTokenService tokens,
      JwtDecoder jwtDecoder,
      AppSecurityProperties properties,
      ProblemAuthenticationEntryPoint entryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    String[] administratorRoles =
        Role.ADMINISTRATORS.stream().map(Role::name).toArray(String[]::new);
    http.csrf(csrf -> csrf.spa())
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize -> {
              authorize.requestMatchers("/actuator/health").permitAll();
              authorize.requestMatchers(PUBLIC_PATHS).permitAll();
              if (properties.swaggerPublic()) {
                authorize.requestMatchers(DOCUMENTATION_PATHS).permitAll();
              } else {
                authorize.requestMatchers(DOCUMENTATION_PATHS).hasRole(Role.ADMIN_NACIONAL.name());
              }
              // Rutas con datos del propio encuestado: el administrador del cuestionario no las
              // lee.
              authorize
                  .requestMatchers(
                      "/api/v1/survey/progress", "/api/v1/survey/pages/**", "/api/v1/survey/finish")
                  .hasRole(Role.ENCUESTADO.name());
              // Los administradores del cuestionario solo consultan; responder es del encuestado.
              authorize
                  .requestMatchers(HttpMethod.GET, "/api/v1/survey/**")
                  .hasAnyRole(Role.ENCUESTADO.name(), Role.ADMIN_CUESTIONARIO.name());
              authorize.requestMatchers("/api/v1/survey/**").hasRole(Role.ENCUESTADO.name());
              authorize.requestMatchers("/api/v1/admin/**").hasAnyRole(administratorRoles);
              authorize.anyRequest().authenticated();
            })
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                    .frameOptions(frame -> frame.deny())
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
        .addFilterAt(
            sessionAuthenticationFilter(jwtDecoder, entryPoint, tokens),
            BearerTokenAuthenticationFilter.class)
        .addFilterAfter(new SessionRenewalFilter(tokens), BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Se registra el filtro a mano y no con oauth2ResourceServer(): ese configurador exime de CSRF a
   * toda petición con token resuelto, y aquí el token viaja en una cookie, que sí exige CSRF.
   */
  private static BearerTokenAuthenticationFilter sessionAuthenticationFilter(
      JwtDecoder jwtDecoder,
      ProblemAuthenticationEntryPoint entryPoint,
      SessionTokenService tokens) {
    JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
    provider.setJwtAuthenticationConverter(jwtAuthenticationConverter(tokens));
    BearerTokenAuthenticationFilter filter =
        new BearerTokenAuthenticationFilter(new ProviderManager(provider));
    filter.setBearerTokenResolver(new CookieBearerTokenResolver(publicPathsMatcher()));
    filter.setAuthenticationEntryPoint(entryPoint);
    filter.setAuthenticationFailureHandler(entryPoint::commence);
    return filter;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(AppSecurityProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(properties.allowedOrigin()));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Correlation-Id"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  static RequestMatcher publicPathsMatcher() {
    PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
    List<RequestMatcher> matchers =
        Arrays.stream(PUBLIC_PATHS).map(path -> (RequestMatcher) builder.matcher(path)).toList();
    return new OrRequestMatcher(matchers);
  }

  /**
   * El sujeto del encuestado se descifra aquí, al autenticar: un token bien firmado pero con un
   * sujeto ilegible (p. ej. emitido antes del cifrado del C_C) responde 401 y no 500.
   */
  static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(
      SessionTokenService tokens) {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(SessionTokenService.ROLES_CLAIM);
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return jwt -> {
      try {
        tokens.principalFrom(jwt);
      } catch (IllegalArgumentException exception) {
        throw new InvalidBearerTokenException("Sesión inválida", exception);
      }
      return converter.convert(jwt);
    };
  }
}
