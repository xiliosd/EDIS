package co.gov.dane.edi.controller;

import co.gov.dane.edi.model.PageDetail;
import co.gov.dane.edi.model.SurveyProgress;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.service.SurveyProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/survey")
@Tag(name = "Encuesta", description = "Lectura del cuestionario")
public class RespondentSurveyController {

  private final EffectiveRulesService rules;
  private final SurveyProgressService progress;
  private final SessionTokenService tokens;

  public RespondentSurveyController(
      EffectiveRulesService rules, SurveyProgressService progress, SessionTokenService tokens) {
    this.rules = rules;
    this.progress = progress;
    this.tokens = tokens;
  }

  @Operation(summary = "Consultar las reglas de saltos y validaciones vigentes")
  @ApiResponse(responseCode = "200", description = "Reglas del archivo más saltos heredados")
  @GetMapping(value = "/rules", produces = MediaType.APPLICATION_JSON_VALUE)
  public RuleSet rules() {
    return rules.effectiveRules();
  }

  @Operation(summary = "Consultar el avance del encuestado")
  @ApiResponse(responseCode = "200", description = "Estado de las 9 páginas y radicado si terminó")
  @GetMapping(value = "/progress", produces = MediaType.APPLICATION_JSON_VALUE)
  public SurveyProgress progress(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return progress.progress(respondentCode(jwt));
  }

  @Operation(summary = "Consultar una página con sus preguntas y, si ya se guardó, sus respuestas")
  @ApiResponse(responseCode = "200", description = "Página")
  @ApiResponse(
      responseCode = "404",
      description = "La página no existe",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(value = "/pages/{pageCode}", produces = MediaType.APPLICATION_JSON_VALUE)
  public PageDetail page(
      @PathVariable String pageCode, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return progress.pageDetail(respondentCode(jwt), pageCode);
  }

  private String respondentCode(Jwt jwt) {
    return tokens.principalFrom(jwt).respondentCode();
  }
}
