package co.gov.dane.edi.operation.controller;

import co.gov.dane.edi.admin.AllowedInReadOnlyQuestionnaire;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.OperationalAccessService;
import co.gov.dane.edi.operation.excel.XlsxWriter;
import co.gov.dane.edi.operation.keys.SampleKeysExcel;
import co.gov.dane.edi.operation.keys.SampleKeysRepository.CredentialRow;
import co.gov.dane.edi.operation.keys.SampleKeysService;
import co.gov.dane.edi.operation.keys.dto.KeysGenerateRequest;
import co.gov.dane.edi.operation.keys.dto.KeysGenerateResult;
import co.gov.dane.edi.operation.keys.dto.KeysSummary;
import co.gov.dane.edi.operation.report.ReportSurvey;
import co.gov.dane.edi.security.SessionTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Claves de la muestra (solo ADMIN_NACIONAL, por SecurityConfig y por el servicio). Todas las
 * respuestas son {@code no-store}; la generación funciona aunque el cuestionario esté en solo
 * lectura (producción). Ninguna URL lleva cédulas, usuarios ni claves.
 */
@RestController
@RequestMapping("/api/v1/admin/keys")
@Tag(name = "Claves", description = "Usuarios y claves de la muestra")
public class SampleKeysController {

  private static final String PROBLEM = "application/problem+json";
  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

  private final SampleKeysService keysService;
  private final OperationalAccessService accessService;
  private final SessionTokenService tokens;
  private final XlsxWriter xlsxWriter;
  private final SurveyProperties survey;
  private final Clock clock;

  public SampleKeysController(
      SampleKeysService keysService,
      OperationalAccessService accessService,
      SessionTokenService tokens,
      XlsxWriter xlsxWriter,
      SurveyProperties survey,
      Clock clock) {
    this.keysService = keysService;
    this.accessService = accessService;
    this.tokens = tokens;
    this.xlsxWriter = xlsxWriter;
    this.survey = survey;
    this.clock = clock;
  }

  @Operation(
      operationId = "keysSummary",
      summary = "Resumen de claves por departamento",
      description =
          "Funcionarios elegibles (carga web y activos, como el login), con usuario y sin usuario,"
              + " por departamento. Solo el administrador nacional.")
  @ApiResponse(responseCode = "200", description = "Resumen del año")
  @GetMapping(path = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<KeysSummary> summary(
      @RequestParam(name = "anio", required = false) Integer year,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(keysService.summary(access, yearOrDefault(year)));
  }

  @Operation(
      operationId = "keysGenerate",
      summary = "Generar usuarios y claves pendientes",
      description =
          "Asigna usuario y clave (8 letras A-Z) a los elegibles del año que no tienen usuario;"
              + " nunca cambia uno existente. expectedWithoutKey es el número que se confirmó en"
              + " pantalla: si cambió, responde 409 sin generar. Funciona aunque el cuestionario"
              + " sea de solo lectura.")
  @ApiResponse(responseCode = "200", description = "Cantidad de claves generadas")
  @ApiResponse(
      responseCode = "403",
      description = "El usuario no es administrador nacional",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El número de funcionarios sin clave cambió desde la confirmación",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @AllowedInReadOnlyQuestionnaire
  @PostMapping(
      path = "/generate",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<KeysGenerateResult> generate(
      @Valid @RequestBody KeysGenerateRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    String username = tokens.principalFrom(jwt).administratorUsername();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(keysService.generate(access, username, request));
  }

  @Operation(
      operationId = "keysExcel",
      summary = "Descargar usuarios y claves de un departamento",
      description =
          "Hoja Claves con entidad, código de entidad, cédula, nombre, usuario y clave de los"
              + " elegibles con usuario del departamento, para la carta al coordinador. Contiene"
              + " datos personales y claves: la descarga no se guarda en caché. 404 si el"
              + " departamento no tiene elegibles en la muestra del año.")
  @ApiResponse(
      responseCode = "200",
      description = "Archivo .xlsx",
      content =
          @Content(
              mediaType = ReportController.XLSX,
              schema = @Schema(type = "string", format = "binary")))
  @ApiResponse(
      responseCode = "404",
      description = "El departamento no tiene elegibles en la muestra del año",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(
      path = "/excel",
      produces = {ReportController.XLSX, PROBLEM})
  public ResponseEntity<byte[]> excel(
      @RequestParam(name = "anio", required = false) Integer year,
      @RequestParam(name = "departamento") @Min(1) @Max(999) int departmentCode,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    String username = tokens.principalFrom(jwt).administratorUsername();
    int keysYear = yearOrDefault(year);
    List<CredentialRow> rows =
        keysService.departmentKeys(access, username, keysYear, departmentCode);
    byte[] content =
        xlsxWriter.write(
            List.of(SampleKeysExcel.sheet(rows, survey.code(), keysYear, departmentCode)));
    // Las claves cubren toda la muestra de la encuesta (sin filtro ENC): "general" en el nombre.
    return ReportController.excelResponse(
        content,
        ReportController.fileName(
            "claves",
            ReportSurvey.GENERAL,
            keysYear,
            departmentCode,
            LocalDate.now(clock.withZone(BOGOTA))));
  }

  private int yearOrDefault(Integer year) {
    return year == null ? survey.year() : year;
  }
}
