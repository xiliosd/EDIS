package co.gov.dane.edi.admin.controller;

import co.gov.dane.edi.admin.AnswerTableMappingService;
import co.gov.dane.edi.admin.YearCopyService;
import co.gov.dane.edi.admin.dto.CreateYearRequest;
import co.gov.dane.edi.admin.dto.TablesRequest;
import co.gov.dane.edi.admin.dto.TablesResult;
import co.gov.dane.edi.admin.dto.YearSummary;
import co.gov.dane.edi.security.SessionTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(
    name = "Administración del cuestionario",
    description = "Consulta y edición de años y árbol del cuestionario (rol ADMIN_CUESTIONARIO)")
public class YearAdminController {

  private static final String TABLES = "/questionnaire/{encuesta}/{anio}/tables";

  private final YearCopyService yearCopy;
  private final AnswerTableMappingService tableMapping;
  private final SessionTokenService tokens;

  public YearAdminController(
      YearCopyService yearCopy,
      AnswerTableMappingService tableMapping,
      SessionTokenService tokens) {
    this.yearCopy = yearCopy;
    this.tableMapping = tableMapping;
    this.tokens = tokens;
  }

  @Operation(
      summary = "Crear el cuestionario de un año a partir de otro",
      description =
          "Copia las preguntas del año de origen y deja el año nuevo en borrador, con los saltos"
              + " heredados como reglas legacy-<COL>. Todo o nada.")
  @ApiResponse(responseCode = "201", description = "Año creado en borrador")
  @ApiResponse(
      responseCode = "400",
      description = "Cuerpo inválido: falta el año nuevo o el de origen",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año de origen no existen",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año ya existe o la encuesta ya tiene un borrador abierto",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Año fuera de rango o año de origen sin preguntas",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "500",
      description = "No se pudo escribir el archivo de reglas; no quedan filas copiadas",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping(value = "/years/{encuesta}", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public YearSummary createYear(
      @PathVariable("encuesta") String survey,
      @Valid @RequestBody CreateYearRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return yearCopy.createYear(survey, request.year(), request.fromYear(), user(jwt));
  }

  @Operation(
      summary = "Elegir la tabla de respuestas de cada capítulo de un año en borrador",
      description =
          "Reemplaza el mapeo del año. Una tabla que aún no existe se acepta y se informa en"
              + " missing; se crea con el script del capítulo.")
  @ApiResponse(responseCode = "200", description = "Mapeo guardado, versión y tablas faltantes")
  @ApiResponse(
      responseCode = "400",
      description = "Cuerpo inválido: faltan las tablas o la versión",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año no existen",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año está publicado o la versión cambió",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description =
          "Capítulo inexistente, nombre de tabla inválido o tabla sin las columnas C_C, ESTADO y"
              + " TC",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping(value = TABLES, produces = MediaType.APPLICATION_JSON_VALUE)
  public TablesResult saveTables(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @Valid @RequestBody TablesRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return tableMapping.saveTables(survey, year, request.tables(), request.version(), user(jwt));
  }

  @Operation(
      summary = "Script CREATE TABLE de la tabla nueva de un capítulo",
      description =
          "Texto plano que ejecuta la persona técnica: copia vacía de la tabla del año anterior.")
  @ApiResponse(
      responseCode = "200",
      description = "Sentencia CREATE TABLE",
      content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE))
  @ApiResponse(
      responseCode = "404",
      description =
          "La encuesta, el año o el capítulo no existen, el capítulo no tiene tabla nueva, la"
              + " tabla ya existe o no hay año anterior",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "La tabla asignada o la del año anterior no son válidas",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(
      value = TABLES + "/{chapterCode}/create-script",
      produces = MediaType.TEXT_PLAIN_VALUE)
  public String createTableScript(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @PathVariable String chapterCode) {
    return tableMapping.createTableScript(survey, year, chapterCode);
  }

  private String user(Jwt jwt) {
    return tokens.principalFrom(jwt).subject();
  }
}
