package co.gov.dane.edi.controller;

import co.gov.dane.edi.model.FinishRequest;
import co.gov.dane.edi.model.FinishResult;
import co.gov.dane.edi.model.PageAnswersRequest;
import co.gov.dane.edi.model.PageDetail;
import co.gov.dane.edi.model.PageSaveResult;
import co.gov.dane.edi.model.SurveyProgress;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.service.SurveyAnswerService;
import co.gov.dane.edi.service.SurveyProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/survey")
@Tag(name = "Encuesta", description = "Lectura y diligenciamiento del cuestionario")
public class RespondentSurveyController {

  private final EffectiveRulesService rules;
  private final SurveyProgressService progress;
  private final SessionTokenService tokens;
  private final SurveyAnswerService answersService;

  public RespondentSurveyController(
      EffectiveRulesService rules,
      SurveyProgressService progress,
      SessionTokenService tokens,
      SurveyAnswerService answersService) {
    this.rules = rules;
    this.progress = progress;
    this.tokens = tokens;
    this.answersService = answersService;
  }

  @Operation(summary = "Consultar las reglas de saltos y validaciones vigentes")
  @ApiResponse(responseCode = "200", description = "Reglas del archivo más saltos heredados")
  @ApiResponse(
      responseCode = "503",
      description = "La encuesta aún no está disponible (el año activo es un borrador)",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(value = "/rules", produces = MediaType.APPLICATION_JSON_VALUE)
  public RuleSet rules() {
    return rules.effectiveRules();
  }

  @Operation(summary = "Consultar el avance del encuestado")
  @ApiResponse(responseCode = "200", description = "Estado de las 9 páginas y radicado si terminó")
  @ApiResponse(
      responseCode = "503",
      description = "La encuesta aún no está disponible (el año activo es un borrador)",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
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
  @ApiResponse(
      responseCode = "503",
      description = "La encuesta aún no está disponible (el año activo es un borrador)",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(value = "/pages/{pageCode}", produces = MediaType.APPLICATION_JSON_VALUE)
  public PageDetail page(
      @PathVariable String pageCode, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return progress.pageDetail(respondentCode(jwt), pageCode);
  }

  @Operation(summary = "Guardar las respuestas de una página (una sola vez)")
  @ApiResponse(responseCode = "200", description = "Página guardada; indica la siguiente")
  @ApiResponse(
      responseCode = "404",
      description = "La página no existe",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Página ya guardada o bloqueada",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Respuestas inválidas (errors: columna → mensaje)",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "503",
      description = "La encuesta aún no está disponible (el año activo es un borrador)",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping(value = "/pages/{pageCode}/answers", produces = MediaType.APPLICATION_JSON_VALUE)
  public PageSaveResult saveAnswers(
      @PathVariable String pageCode,
      @Valid @RequestBody PageAnswersRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return answersService.savePage(respondentCode(jwt), pageCode, request.answers());
  }

  @Operation(summary = "Terminar la encuesta y obtener el radicado")
  @ApiResponse(responseCode = "200", description = "Radicado de la encuesta")
  @ApiResponse(
      responseCode = "409",
      description = "Faltan páginas por guardar",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "503",
      description = "La encuesta aún no está disponible (el año activo es un borrador)",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping(value = "/finish", produces = MediaType.APPLICATION_JSON_VALUE)
  public FinishResult finish(
      @Valid @RequestBody FinishRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return answersService.finish(respondentCode(jwt), request.observations());
  }

  private String respondentCode(Jwt jwt) {
    return tokens.principalFrom(jwt).respondentCode();
  }
}
