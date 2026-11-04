package co.gov.dane.edi.admin.controller;

import co.gov.dane.edi.admin.HistoryService;
import co.gov.dane.edi.admin.RulesAdminService;
import co.gov.dane.edi.admin.dto.HistoryView;
import co.gov.dane.edi.admin.dto.RestoreRequest;
import co.gov.dane.edi.admin.dto.RulesEditResult;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.security.SessionTokenService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/questionnaire/{encuesta}/{anio}/history")
@Tag(
    name = "Historial del cuestionario",
    description =
        "Versiones del archivo de reglas de un año y su restauración en borrador (rol"
            + " ADMIN_CUESTIONARIO)")
public class HistoryAdminController {

  private static final String PROBLEM = "application/problem+json";

  private final HistoryService history;
  private final RulesAdminService rules;
  private final SessionTokenService tokens;

  public HistoryAdminController(
      HistoryService history, RulesAdminService rules, SessionTokenService tokens) {
    this.history = history;
    this.rules = rules;
    this.tokens = tokens;
  }

  @Operation(
      operationId = "historyView",
      summary = "Consultar el historial de versiones de un año",
      description =
          "La versión vigente, el estado del año y cada versión guardada con quién, cuándo, la"
              + " operación y, en las correcciones, el motivo; de la más reciente a la más"
              + " antigua.")
  @ApiResponse(responseCode = "200", description = "Historial del año")
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año no existen",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public HistoryView view(@PathVariable("encuesta") String survey, @PathVariable("anio") int year) {
    return history.view(survey, year);
  }

  @Operation(
      operationId = "historyVersion",
      summary = "Leer las reglas de una versión del historial",
      description = "Devuelve el archivo de reglas tal como estaba guardado en esa versión.")
  @ApiResponse(responseCode = "200", description = "Reglas de la versión")
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta, el año o la versión no existen",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(path = "/{version}", produces = MediaType.APPLICATION_JSON_VALUE)
  public RuleSet version(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable("version") int version) {
    return history.version(survey, year, version);
  }

  @Operation(
      operationId = "historyRestore",
      summary = "Restaurar una versión del historial en un borrador",
      description =
          "Reemplaza saltos, validaciones, casos, tablas y columnas nuevas por los de la versión"
              + " elegida y guarda el resultado como versión nueva. Conserva el estado y no"
              + " restaura las filas FRM_*. Pasa por el mismo análisis que cualquier cambio:"
              + " los errores nuevos lo rechazan.")
  @ApiResponse(responseCode = "200", description = "Versión restaurada; análisis y casos")
  @ApiResponse(
      responseCode = "400",
      description = "Cuerpo inválido: falta la versión",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta, el año o la versión no existen",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado o la versión vigente cambió",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "La restauración agrega errores de análisis (propiedad report)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping(path = "/{version}/restore", produces = MediaType.APPLICATION_JSON_VALUE)
  public RulesEditResult restore(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable("version") int version,
      @Valid @RequestBody RestoreRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return rules.restore(survey, year, version, request.version(), user(jwt));
  }

  private String user(Jwt jwt) {
    return tokens.principalFrom(jwt).subject();
  }
}
