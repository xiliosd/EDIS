package co.gov.dane.edi.admin.controller;

import co.gov.dane.edi.admin.RulesAdminService;
import co.gov.dane.edi.admin.dto.ImportRulesRequest;
import co.gov.dane.edi.admin.dto.RulesEditResult;
import co.gov.dane.edi.admin.dto.RulesEditorView;
import co.gov.dane.edi.admin.dto.SkipRuleRequest;
import co.gov.dane.edi.admin.dto.TestCaseRequest;
import co.gov.dane.edi.admin.dto.ValidationRequest;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.security.SessionTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/questionnaire/{encuesta}/{anio}/rules")
@Tag(
    name = "Administración de reglas",
    description =
        "Reglas de salto, validaciones y casos de prueba de un año (rol ADMIN_CUESTIONARIO)")
public class RulesAdminController {

  private static final String PROBLEM = "application/problem+json";

  private final RulesAdminService rules;
  private final SessionTokenService tokens;

  public RulesAdminController(RulesAdminService rules, SessionTokenService tokens) {
    this.rules = rules;
    this.tokens = tokens;
  }

  @Operation(
      summary = "Consultar las reglas, validaciones y casos de un año",
      description =
          "Incluye las reglas efectivas (archivo más heredadas), el análisis de lógica, el"
              + " resultado de cada caso y las preguntas del año. También responde para años"
              + " publicados, en modo de solo lectura.")
  @ApiResponse(responseCode = "200", description = "Reglas del año con su análisis")
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año no existen",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public RulesEditorView view(
      @PathVariable("encuesta") String survey, @PathVariable("anio") int year) {
    return rules.view(survey, year);
  }

  @Operation(
      summary = "Guardar una regla de salto",
      description =
          "Crea la regla o reemplaza la que tiene el mismo id. Se rechaza con 422 si agrega"
              + " errores que el archivo vigente no tenía; los avisos nunca bloquean.")
  @ApiResponse(responseCode = "200", description = "Versión nueva, análisis y resultado de casos")
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año no existen",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado o la versión cambió",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "El id no coincide, no es válido o la regla agrega errores (propiedad report)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping(value = "/skips/{ruleId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public RulesEditResult saveSkip(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable String ruleId,
      @Valid @RequestBody SkipRuleRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return rules.saveSkip(survey, year, ruleId, request.rule(), request.version(), user(jwt));
  }

  @Operation(summary = "Borrar una regla de salto del archivo del año")
  @ApiResponse(responseCode = "200", description = "Versión nueva, análisis y resultado de casos")
  @ApiResponse(
      responseCode = "404",
      description = "La regla no existe en el archivo del año",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado o la versión cambió",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Al borrarla quedan errores nuevos (propiedad report)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @DeleteMapping(value = "/skips/{ruleId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public RulesEditResult deleteSkip(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable String ruleId,
      @RequestParam int version,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return rules.deleteSkip(survey, year, ruleId, version, user(jwt));
  }

  @Operation(
      summary = "Guardar la validación de una columna",
      description = "Crea la validación o reemplaza la de la columna indicada.")
  @ApiResponse(responseCode = "200", description = "Versión nueva, análisis y resultado de casos")
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado o la versión cambió",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "La columna no es válida o la validación agrega errores (propiedad report)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping(value = "/validations/{column}", produces = MediaType.APPLICATION_JSON_VALUE)
  public RulesEditResult saveValidation(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable String column,
      @Valid @RequestBody ValidationRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return rules.saveValidation(
        survey, year, column, request.validation(), request.version(), user(jwt));
  }

  @Operation(summary = "Borrar la validación de una columna")
  @ApiResponse(responseCode = "200", description = "Versión nueva, análisis y resultado de casos")
  @ApiResponse(
      responseCode = "404",
      description = "La columna no tiene validación en el archivo del año",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado o la versión cambió",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @DeleteMapping(value = "/validations/{column}", produces = MediaType.APPLICATION_JSON_VALUE)
  public RulesEditResult deleteValidation(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable String column,
      @RequestParam int version,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return rules.deleteValidation(survey, year, column, version, user(jwt));
  }

  @Operation(
      summary = "Guardar un caso de prueba",
      description = "Crea el caso o reemplaza el que tiene el mismo id y lo da por revisado.")
  @ApiResponse(responseCode = "200", description = "Versión nueva, análisis y resultado de casos")
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado o la versión cambió",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "El id no coincide o no es válido, o el caso no es válido",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping(value = "/cases/{caseId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public RulesEditResult saveCase(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable String caseId,
      @Valid @RequestBody TestCaseRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return rules.saveCase(survey, year, caseId, request.testCase(), request.version(), user(jwt));
  }

  @Operation(summary = "Borrar un caso de prueba")
  @ApiResponse(responseCode = "200", description = "Versión nueva, análisis y resultado de casos")
  @ApiResponse(
      responseCode = "404",
      description = "El caso no existe en el archivo del año",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado o la versión cambió",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @DeleteMapping(value = "/cases/{caseId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public RulesEditResult deleteCase(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable String caseId,
      @RequestParam int version,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return rules.deleteCase(survey, year, caseId, version, user(jwt));
  }

  @Operation(
      summary = "Descargar el archivo de reglas del año",
      description = "El archivo guardado, con saltos, validaciones y casos, como adjunto JSON.")
  @ApiResponse(
      responseCode = "200",
      description = "Archivo EDIN-AAAA.json",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = RuleSet.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año no existen",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<byte[]> export(
      @PathVariable("encuesta") String survey, @PathVariable("anio") int year) {
    byte[] content = rules.export(survey, year);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(survey + "-" + year + ".json")
                .build()
                .toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(content);
  }

  @Operation(
      summary = "Importar reglas, validaciones y casos",
      description =
          "Reemplaza solo los saltos, las validaciones y los casos del año en borrador. El estado,"
              + " las tablas y las columnas nuevas del archivo importado se ignoran. Se rechaza con"
              + " 422 si el análisis encuentra errores nuevos, por ejemplo un ciclo entre reglas.")
  @ApiResponse(responseCode = "200", description = "Versión nueva, análisis y resultado de casos")
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado o la versión cambió",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "413",
      description = "El cuerpo supera 1 MB",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "El archivo es de otra encuesta o año, o agrega errores (propiedad report)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping(value = "/import", produces = MediaType.APPLICATION_JSON_VALUE)
  public RulesEditResult importRules(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @Valid @RequestBody ImportRulesRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return rules.importRules(survey, year, request.rules(), request.version(), user(jwt));
  }

  private String user(Jwt jwt) {
    return tokens.principalFrom(jwt).subject();
  }
}
