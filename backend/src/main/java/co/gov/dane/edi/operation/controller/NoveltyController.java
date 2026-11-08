package co.gov.dane.edi.operation.controller;

import co.gov.dane.edi.admin.AllowedInReadOnlyQuestionnaire;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.OperationalAccessService;
import co.gov.dane.edi.operation.novelty.NoveltyService;
import co.gov.dane.edi.operation.novelty.dto.NoveltyCatalogEntry;
import co.gov.dane.edi.operation.novelty.dto.NoveltyChangeRequest;
import co.gov.dane.edi.operation.novelty.dto.NoveltyEntity;
import co.gov.dane.edi.operation.novelty.dto.NoveltyPeopleRequest;
import co.gov.dane.edi.operation.novelty.dto.NoveltyPerson;
import co.gov.dane.edi.operation.novelty.dto.NoveltyRemoveRequest;
import co.gov.dane.edi.security.SessionTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de novedades (solo ADMIN_TERRITORIAL, por SecurityConfig). La cédula viaja solo en el
 * cuerpo de los POST, nunca en la URL; todas las respuestas son {@code no-store}. Las escrituras
 * funcionan aunque el cuestionario esté en solo lectura (producción).
 */
@RestController
@RequestMapping("/api/v1/admin/novelties")
@Tag(name = "Novedades", description = "Gestión de novedades de la muestra por la territorial")
public class NoveltyController {

  private static final String PROBLEM = "application/problem+json";

  private final NoveltyService noveltyService;
  private final OperationalAccessService accessService;
  private final SessionTokenService tokens;
  private final SurveyProperties survey;

  public NoveltyController(
      NoveltyService noveltyService,
      OperationalAccessService accessService,
      SessionTokenService tokens,
      SurveyProperties survey) {
    this.noveltyService = noveltyService;
    this.accessService = accessService;
    this.tokens = tokens;
    this.survey = survey;
  }

  @Operation(
      operationId = "noveltyCatalog",
      summary = "Catálogo de novedades",
      description =
          "Novedades de GRAL_NOVEDAD de la encuesta y el año; requiresText marca la de \"otra"
              + " causa\". Solo el administrador territorial.")
  @ApiResponse(responseCode = "200", description = "Catálogo del año")
  @GetMapping(path = "/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<NoveltyCatalogEntry>> catalog(
      @RequestParam(name = "anio", required = false) Integer year,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    return noStore(noveltyService.catalog(access, yearOrDefault(year)));
  }

  @Operation(
      operationId = "noveltyEntities",
      summary = "Entidades de un departamento",
      description =
          "Entidades con funcionarios activos en el departamento, con el tamaño de la muestra y"
              + " cuántos tienen novedad. Un departamento ajeno devuelve 403.")
  @ApiResponse(responseCode = "200", description = "Entidades del departamento")
  @GetMapping(path = "/entities", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<NoveltyEntity>> entities(
      @RequestParam(name = "anio", required = false) Integer year,
      @RequestParam(name = "departamento") int departmentCode,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    return noStore(noveltyService.entities(access, yearOrDefault(year), departmentCode));
  }

  @Operation(
      operationId = "noveltyPeople",
      summary = "Funcionarios de una entidad con su novedad",
      description =
          "Consulta (no escribe): va por POST para que el filtro no quede en la URL. Contiene"
              + " datos personales: la respuesta no se guarda en caché. Un departamento ajeno"
              + " devuelve 403.")
  @ApiResponse(responseCode = "200", description = "Funcionarios activos de la entidad")
  @ApiResponse(
      responseCode = "403",
      description = "El departamento no es del alcance del usuario",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Las tablas del año no tienen B07 o H06",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @AllowedInReadOnlyQuestionnaire
  @PostMapping(
      path = "/people",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<NoveltyPerson>> people(
      @Valid @RequestBody NoveltyPeopleRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    return noStore(
        noveltyService.people(
            access, yearOrDefault(request.anio()), request.departamento(), request.entidad()));
  }

  @Operation(
      operationId = "noveltyAssign",
      summary = "Asignar o modificar la novedad de un funcionario",
      description =
          "Por cédula y año (en el cuerpo). La novedad debe existir en el catálogo del año y la de"
              + " \"otra causa\" exige texto (máximo 500 bytes Latin-9). Si la novedad cambió"
              + " desde que se cargó la pantalla responde 409. Funciona aunque el cuestionario sea"
              + " de solo lectura.")
  @ApiResponse(responseCode = "204", description = "Novedad guardada")
  @ApiResponse(
      responseCode = "403",
      description = "El usuario no gestiona novedades",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "El funcionario no está en la muestra de los departamentos del usuario",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "La novedad cambió desde que se cargó la pantalla",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Novedad fuera del catálogo o causa vacía o demasiado larga",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @AllowedInReadOnlyQuestionnaire
  @PostMapping(path = "/assign", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> assign(
      @Valid @RequestBody NoveltyChangeRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    noveltyService.assign(access, username(jwt), request);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @Operation(
      operationId = "noveltyRemove",
      summary = "Quitar la novedad de un funcionario",
      description =
          "Limpia la novedad y la causa. Mismas reglas de alcance y concurrencia que asignar.")
  @ApiResponse(responseCode = "204", description = "Novedad quitada")
  @ApiResponse(
      responseCode = "403",
      description = "El usuario no gestiona novedades",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "El funcionario no está en la muestra de los departamentos del usuario",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "La novedad cambió desde que se cargó la pantalla",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @AllowedInReadOnlyQuestionnaire
  @PostMapping(path = "/remove", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> remove(
      @Valid @RequestBody NoveltyRemoveRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    noveltyService.remove(access, username(jwt), request);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  private String username(Jwt jwt) {
    return tokens.principalFrom(jwt).administratorUsername();
  }

  private int yearOrDefault(Integer year) {
    return year == null ? survey.year() : year;
  }

  private static <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
