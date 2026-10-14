package co.gov.dane.edi.controller;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.AccountType;
import co.gov.dane.edi.model.LoginRequest;
import co.gov.dane.edi.model.SessionResponse;
import co.gov.dane.edi.security.AuthService;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticación", description = "Inicio y cierre de sesión")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final SessionTokenService tokens;
  private final SurveyProperties survey;

  public AuthController(
      AuthService authService, SessionTokenService tokens, SurveyProperties survey) {
    this.authService = authService;
    this.tokens = tokens;
    this.survey = survey;
  }

  @Operation(summary = "Iniciar sesión", description = "Emite la cookie de sesión EDI_SESSION.")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "Sesión iniciada")
  @ApiResponse(
      responseCode = "401",
      description = "Usuario o clave incorrectos",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "429",
      description = "Demasiados intentos; ver Retry-After",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SessionResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    SessionPrincipal principal = authService.authenticate(request, httpRequest.getRemoteAddr());
    String token = tokens.issueToken(principal);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, tokens.sessionCookie(token).toString())
        .body(toResponse(principal));
  }

  @Operation(summary = "Cerrar sesión")
  @SecurityRequirements
  @ApiResponse(responseCode = "204", description = "Sesión cerrada")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, tokens.expiredSessionCookie().toString())
        .build();
  }

  @Operation(summary = "Consultar la sesión actual")
  @ApiResponse(responseCode = "200", description = "Sesión vigente")
  @ApiResponse(
      responseCode = "401",
      description = "Sin sesión",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
  public SessionResponse me(@AuthenticationPrincipal Jwt jwt) {
    return toResponse(SessionPrincipal.fromJwt(jwt));
  }

  private SessionResponse toResponse(SessionPrincipal principal) {
    return new SessionResponse(
        principal.isRespondent() ? AccountType.RESPONDENT : AccountType.ADMINISTRATOR,
        principal.roles().stream().map(Role::name).sorted().toList(),
        survey.code(),
        survey.year(),
        principal.departmentCode());
  }
}
