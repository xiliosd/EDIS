package co.gov.dane.edi.admin.controller;

import co.gov.dane.edi.admin.QuestionnaireAdminService;
import co.gov.dane.edi.admin.YearCatalogService;
import co.gov.dane.edi.admin.dto.AdminQuestionnaireView;
import co.gov.dane.edi.admin.dto.YearSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(
    name = "Administración del cuestionario",
    description = "Consulta de años y árbol del cuestionario (rol ADMIN_CUESTIONARIO)")
public class QuestionnaireAdminController {

  private final YearCatalogService years;
  private final QuestionnaireAdminService questionnaire;

  public QuestionnaireAdminController(
      YearCatalogService years, QuestionnaireAdminService questionnaire) {
    this.years = years;
    this.questionnaire = questionnaire;
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
}
