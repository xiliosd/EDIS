package co.gov.dane.edi.controller;

import co.gov.dane.edi.model.ChapterDetail;
import co.gov.dane.edi.model.ChapterSummary;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.service.QuestionnaireService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/survey")
@Tag(name = "Encuesta", description = "Lectura del cuestionario")
public class SurveyController {

  private final QuestionnaireService questionnaireService;

  public SurveyController(QuestionnaireService questionnaireService) {
    this.questionnaireService = questionnaireService;
  }

  @Operation(summary = "Listar los capítulos del cuestionario")
  @ApiResponse(responseCode = "200", description = "Capítulos B a H")
  @GetMapping(value = "/chapters", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ChapterSummary> listChapters() {
    return questionnaireService.listChapters();
  }

  @Operation(
      summary = "Consultar un capítulo",
      description = "Las preguntas sin columna solo las ve el administrador del cuestionario.")
  @ApiResponse(responseCode = "200", description = "Capítulo con sus preguntas")
  @ApiResponse(
      responseCode = "404",
      description = "El capítulo no existe",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(value = "/chapters/{chapterCode}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ChapterDetail getChapter(
      @PathVariable String chapterCode, @Parameter(hidden = true) Authentication authentication) {
    boolean includePending =
        authentication.getAuthorities().stream()
            .anyMatch(a -> Role.ADMIN_CUESTIONARIO.authority().equals(a.getAuthority()));
    return questionnaireService.getChapter(chapterCode, includePending);
  }
}
