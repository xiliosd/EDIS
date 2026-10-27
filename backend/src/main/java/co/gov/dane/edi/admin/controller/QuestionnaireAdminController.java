package co.gov.dane.edi.admin.controller;

import co.gov.dane.edi.admin.QuestionAdminService;
import co.gov.dane.edi.admin.QuestionnaireAdminService;
import co.gov.dane.edi.admin.YearCatalogService;
import co.gov.dane.edi.admin.dto.AdminEditResult;
import co.gov.dane.edi.admin.dto.AdminQuestionnaireView;
import co.gov.dane.edi.admin.dto.EditTextRequest;
import co.gov.dane.edi.admin.dto.ReorderRequest;
import co.gov.dane.edi.admin.dto.YearSummary;
import co.gov.dane.edi.security.SessionTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(
    name = "Administración del cuestionario",
    description = "Consulta y edición de años y árbol del cuestionario (rol ADMIN_CUESTIONARIO)")
public class QuestionnaireAdminController {

  private final YearCatalogService years;
  private final QuestionnaireAdminService questionnaire;
  private final QuestionAdminService edits;
  private final SessionTokenService tokens;

  public QuestionnaireAdminController(
      YearCatalogService years,
      QuestionnaireAdminService questionnaire,
      QuestionAdminService edits,
      SessionTokenService tokens) {
    this.years = years;
    this.questionnaire = questionnaire;
    this.edits = edits;
    this.tokens = tokens;
  }

  @Operation(summary = "Listar los años del cuestionario de una encuesta")
  @ApiResponse(responseCode = "200", description = "Años con estado, versión y año activo")
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta no existe",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(value = "/years/{encuesta}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<YearSummary> years(@PathVariable("encuesta") String survey) {
    return years.years(survey);
  }

  @Operation(summary = "Consultar el árbol del cuestionario de una encuesta y año")
  @ApiResponse(responseCode = "200", description = "Páginas, preguntas y estado de sus columnas")
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año no existen",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(
      value = "/questionnaire/{encuesta}/{anio}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminQuestionnaireView questionnaire(
      @PathVariable("encuesta") String survey, @PathVariable("anio") int year) {
    return questionnaire.view(survey, year);
  }

  private static final String EDIT_BASE = "/questionnaire/{encuesta}/{anio}";

  @Operation(summary = "Editar el texto de una pregunta de un año en borrador")
  @ApiResponse(responseCode = "200", description = "Página actualizada y nueva versión del año")
  @ApiResponse(
      responseCode = "400",
      description = "Cuerpo inválido: falta el texto o la versión, o el texto excede el límite",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta, el año, la página o el registro no existen en ese año",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado, la versión cambió o la fila la usa otro año",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "El texto queda vacío o es demasiado largo",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping(
      value = EDIT_BASE + "/entries/{capProyId}/text",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminEditResult editQuestionText(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable long capProyId,
      @Valid @RequestBody EditTextRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return edits.editQuestionText(
        survey, year, capProyId, request.text(), request.version(), user(jwt));
  }

  @Operation(summary = "Editar un encabezado de un año en borrador")
  @ApiResponse(responseCode = "200", description = "Página actualizada y nueva versión del año")
  @ApiResponse(
      responseCode = "400",
      description = "Cuerpo inválido: falta el texto o la versión, o el texto excede el límite",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta, el año, la página o el registro no existen en ese año",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado, la versión cambió o la fila la usa otro año",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "El texto queda vacío o es demasiado largo",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping(value = EDIT_BASE + "/headers/{codEnc}", produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminEditResult editHeader(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable long codEnc,
      @Valid @RequestBody EditTextRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return edits.editHeader(survey, year, codEnc, request.text(), request.version(), user(jwt));
  }

  @Operation(summary = "Editar la ayuda de una pregunta de un año en borrador")
  @ApiResponse(responseCode = "200", description = "Página actualizada y nueva versión del año")
  @ApiResponse(
      responseCode = "400",
      description = "Cuerpo inválido: falta el texto o la versión, o el texto excede el límite",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta, el año, la página o el registro no existen en ese año",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado, la versión cambió o la fila la usa otro año",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "El texto queda vacío o es demasiado largo",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping(value = EDIT_BASE + "/help/{codAyu}", produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminEditResult editHelp(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable long codAyu,
      @Valid @RequestBody EditTextRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return edits.editHelp(survey, year, codAyu, request.text(), request.version(), user(jwt));
  }

  @Operation(summary = "Reordenar las preguntas de una página de un año en borrador")
  @ApiResponse(
      responseCode = "200",
      description = "Página actualizada, nueva versión y saltos cuyo destino queda antes")
  @ApiResponse(
      responseCode = "400",
      description = "Cuerpo inválido: falta el texto o la versión, o el texto excede el límite",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta, el año, la página o el registro no existen en ese año",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado, la versión cambió o la fila la usa otro año",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description =
          "La lista no coincide con las preguntas de la página o mueve una pregunta de grupo",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @PatchMapping(
      value = EDIT_BASE + "/pages/{pageCode}/order",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminEditResult reorderPage(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable String pageCode,
      @Valid @RequestBody ReorderRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return edits.reorderPage(
        survey, year, pageCode, request.capProyIds(), request.version(), user(jwt));
  }

  private String user(Jwt jwt) {
    return tokens.principalFrom(jwt).subject();
  }
}
