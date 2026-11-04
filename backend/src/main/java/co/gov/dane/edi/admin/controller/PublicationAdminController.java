package co.gov.dane.edi.admin.controller;

import co.gov.dane.edi.admin.PublicationService;
import co.gov.dane.edi.admin.dto.PublicationChecklist;
import co.gov.dane.edi.admin.dto.PublishRequest;
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
@RequestMapping("/api/v1/admin/questionnaire/{encuesta}/{anio}/publication")
@Tag(
    name = "Publicación del cuestionario",
    description = "Lista de comprobación y publicación de un año (rol ADMIN_CUESTIONARIO)")
public class PublicationAdminController {

  private static final String PROBLEM = "application/problem+json";

  private final PublicationService publication;
  private final SessionTokenService tokens;

  public PublicationAdminController(PublicationService publication, SessionTokenService tokens) {
    this.publication = publication;
    this.tokens = tokens;
  }

  @Operation(
      operationId = "publicationChecklist",
      summary = "Consultar la lista de comprobación para publicar un año",
      description =
          "Cuatro condiciones: análisis sin errores, casos de prueba en verde, columnas creadas y"
              + " tablas válidas. Los avisos del análisis no bloquean. También responde para años"
              + " publicados, de forma informativa.")
  @ApiResponse(responseCode = "200", description = "Lista de comprobación del año")
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año no existen",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public PublicationChecklist checklist(
      @PathVariable("encuesta") String survey, @PathVariable("anio") int year) {
    return publication.checklist(survey, year);
  }

  @Operation(
      operationId = "publishYear",
      summary = "Publicar el cuestionario de un año en borrador",
      description =
          "Recalcula la lista dentro del candado de administración. Si está completa, pasa el año"
              + " a publicado y guarda quién, cuándo, la versión y los avisos. Un año publicado"
              + " solo admite correcciones.")
  @ApiResponse(responseCode = "200", description = "Año publicado; lista de comprobación vigente")
  @ApiResponse(
      responseCode = "400",
      description = "Cuerpo inválido: falta la versión",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año no existen",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año ya está publicado o la versión cambió",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "El año no cumple las condiciones (propiedad checklist)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public PublicationChecklist publish(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @Valid @RequestBody PublishRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return publication.publish(survey, year, request.version(), user(jwt));
  }

  private String user(Jwt jwt) {
    return tokens.principalFrom(jwt).subject();
  }
}
