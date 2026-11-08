package co.gov.dane.edi.operation.controller;

import co.gov.dane.edi.admin.AllowedInReadOnlyQuestionnaire;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.OperationalAccessService;
import co.gov.dane.edi.operation.excel.XlsxWriter;
import co.gov.dane.edi.operation.report.CoverageCut;
import co.gov.dane.edi.operation.report.CoverageCutService;
import co.gov.dane.edi.operation.report.CoverageExcel;
import co.gov.dane.edi.operation.report.CoverageService;
import co.gov.dane.edi.operation.report.OperationalStatusExcel;
import co.gov.dane.edi.operation.report.OperationalStatusService;
import co.gov.dane.edi.operation.report.ReportSurvey;
import co.gov.dane.edi.operation.report.ReportYearsRepository;
import co.gov.dane.edi.operation.report.dto.CoverageReport;
import co.gov.dane.edi.operation.report.dto.CutRequest;
import co.gov.dane.edi.operation.report.dto.CutSummary;
import co.gov.dane.edi.operation.report.dto.PersonStatusPage;
import co.gov.dane.edi.operation.report.dto.StatusFilter;
import co.gov.dane.edi.security.SessionTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/reports")
@Tag(name = "Reportes", description = "Reportes operativos de la muestra (consulta y Excel)")
public class ReportController {

  public static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final String PROBLEM = "application/problem+json";
  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final int MAX_PAGE_SIZE = 100;

  private final OperationalAccessService accessService;
  private final CoverageService coverageService;
  private final OperationalStatusService statusService;
  private final CoverageCutService cutService;
  private final SessionTokenService tokens;
  private final ReportYearsRepository yearsRepository;
  private final XlsxWriter xlsxWriter;
  private final SurveyProperties survey;
  private final Clock clock;

  public ReportController(
      OperationalAccessService accessService,
      CoverageService coverageService,
      OperationalStatusService statusService,
      CoverageCutService cutService,
      SessionTokenService tokens,
      ReportYearsRepository yearsRepository,
      XlsxWriter xlsxWriter,
      SurveyProperties survey,
      Clock clock) {
    this.accessService = accessService;
    this.coverageService = coverageService;
    this.statusService = statusService;
    this.cutService = cutService;
    this.tokens = tokens;
    this.yearsRepository = yearsRepository;
    this.xlsxWriter = xlsxWriter;
    this.survey = survey;
    this.clock = clock;
  }

  @Operation(
      operationId = "reportYears",
      summary = "Años disponibles para los reportes",
      description =
          "Años con funcionarios en la muestra, del más reciente al más antiguo. El administrador"
              + " del cuestionario recibe 403.")
  @ApiResponse(responseCode = "200", description = "Años de la muestra")
  @GetMapping(path = "/years", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<Integer> years(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    // Falla con 403 si el rol no tiene alcance operativo.
    accessService.current(jwt);
    return yearsRepository.findYears();
  }

  @Operation(
      operationId = "reportCoverage",
      summary = "Cobertura por departamento y entidad",
      description =
          "Muestra, novedades, completas, incompletas y sin diligenciar con el criterio único de"
              + " estado. El territorial solo ve sus departamentos; pedir otro devuelve 403.")
  @ApiResponse(responseCode = "200", description = "Cobertura del alcance del usuario")
  @ApiResponse(
      responseCode = "400",
      description = "La encuesta no es GENERAL, EDI ni EDID",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Las tablas del año no tienen B07 o H06",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(path = "/coverage", produces = MediaType.APPLICATION_JSON_VALUE)
  public CoverageReport coverage(
      @RequestParam(name = "encuesta", defaultValue = "GENERAL") ReportSurvey reportSurvey,
      @RequestParam(name = "anio", required = false) Integer year,
      @RequestParam(name = "departamento", required = false) Integer departmentCode,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return buildCoverage(reportSurvey, year, departmentCode, jwt);
  }

  @Operation(
      operationId = "reportCoverageExcel",
      summary = "Cobertura en Excel",
      description =
          "Hojas Cobertura (por entidad y nivel, con total por departamento) y Consolidado"
              + " nacional. Mismas reglas de acceso que el reporte en pantalla.")
  @ApiResponse(
      responseCode = "200",
      description = "Archivo .xlsx",
      content = @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary")))
  @ApiResponse(
      responseCode = "400",
      description = "La encuesta no es GENERAL, EDI ni EDID",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(
      path = "/coverage/excel",
      produces = {XLSX, PROBLEM})
  public ResponseEntity<byte[]> coverageExcel(
      @RequestParam(name = "encuesta", defaultValue = "GENERAL") ReportSurvey reportSurvey,
      @RequestParam(name = "anio", required = false) Integer year,
      @RequestParam(name = "departamento", required = false) Integer departmentCode,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    CoverageReport report = buildCoverage(reportSurvey, year, departmentCode, jwt);
    byte[] content = xlsxWriter.write(CoverageExcel.sheets(report));
    String fileName =
        fileName("cobertura", reportSurvey, report.year(), departmentCode, LocalDate.now(clock));
    return excelResponse(content, fileName);
  }

  @Operation(
      operationId = "reportOperationalStatus",
      summary = "Estado de mi operativo (listado nominal)",
      description =
          "Funcionarios del departamento con su estado (completo, con novedad, incompleto o sin"
              + " diligenciar), filtrable por entidad y estado y paginado (máximo 100 por"
              + " página). Contiene datos personales: la respuesta no se guarda en caché. Un"
              + " departamento fuera del alcance del usuario devuelve 403.")
  @ApiResponse(responseCode = "200", description = "Página del listado")
  @ApiResponse(
      responseCode = "400",
      description = "Parámetros inválidos (departamento obligatorio, size mayor a 100, page < 0)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Las tablas del año no tienen B07 o H06",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(path = "/operational-status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PersonStatusPage> operationalStatus(
      @RequestParam(name = "encuesta", defaultValue = "GENERAL") ReportSurvey reportSurvey,
      @RequestParam(name = "anio", required = false) Integer year,
      @RequestParam(name = "departamento") Integer departmentCode,
      @RequestParam(name = "entidad", required = false) Long entityCode,
      @RequestParam(name = "estado", defaultValue = "ALL") StatusFilter statusFilter,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "100") int size,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "page debe ser 0 o más y size entre 1 y 100.");
    }
    OperationalAccess access = accessService.current(jwt);
    int reportYear = year == null ? survey.year() : year;
    PersonStatusPage result =
        statusService.page(
            access, reportSurvey, reportYear, departmentCode, entityCode, statusFilter, page, size);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
  }

  @Operation(
      operationId = "reportOperationalStatusExcel",
      summary = "Estado de mi operativo en Excel",
      description =
          "Todas las filas del filtro (sin paginar) en la hoja Estado operativo. Contiene datos"
              + " personales: la descarga no se guarda en caché. Mismas reglas de acceso que el"
              + " listado.")
  @ApiResponse(
      responseCode = "200",
      description = "Archivo .xlsx",
      content = @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary")))
  @ApiResponse(
      responseCode = "400",
      description = "Parámetros inválidos",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(
      path = "/operational-status/excel",
      produces = {XLSX, PROBLEM})
  public ResponseEntity<byte[]> operationalStatusExcel(
      @RequestParam(name = "encuesta", defaultValue = "GENERAL") ReportSurvey reportSurvey,
      @RequestParam(name = "anio", required = false) Integer year,
      @RequestParam(name = "departamento") Integer departmentCode,
      @RequestParam(name = "entidad", required = false) Long entityCode,
      @RequestParam(name = "estado", defaultValue = "ALL") StatusFilter statusFilter,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    int reportYear = year == null ? survey.year() : year;
    var rows =
        statusService.all(
            access, reportSurvey, reportYear, departmentCode, entityCode, statusFilter);
    byte[] content =
        xlsxWriter.write(
            List.of(OperationalStatusExcel.sheet(rows, reportSurvey, reportYear, departmentCode)));
    String fileName =
        fileName(
            "estado-operativo",
            reportSurvey,
            reportYear,
            departmentCode,
            LocalDate.now(clock.withZone(BOGOTA)));
    return excelResponse(content, fileName);
  }

  @Operation(
      operationId = "reportCutCreate",
      summary = "Generar un corte de cobertura",
      description =
          "Guarda una fotografía de la cobertura de un departamento en el servidor (solo cifras,"
              + " sin datos personales). Funciona aunque el cuestionario sea de solo lectura. Un"
              + " departamento fuera del alcance del usuario devuelve 403.")
  @ApiResponse(responseCode = "200", description = "Corte generado")
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
      path = "/cuts",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public CutSummary createCut(
      @Valid @RequestBody CutRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    String username = tokens.principalFrom(jwt).administratorUsername();
    return cutService.create(access, request, username);
  }

  @Operation(
      operationId = "reportCutList",
      summary = "Histórico de cortes de cobertura",
      description =
          "Cortes del año, el más reciente primero. El territorial solo ve los de sus"
              + " departamentos; pedir otro devuelve 403.")
  @ApiResponse(responseCode = "200", description = "Cortes del alcance del usuario")
  @GetMapping(path = "/cuts", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CutSummary> cuts(
      @RequestParam(name = "anio", required = false) Integer year,
      @RequestParam(name = "departamento", required = false) Integer departmentCode,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    return cutService.list(access, year == null ? survey.year() : year, departmentCode);
  }

  @Operation(
      operationId = "reportCutExcel",
      summary = "Descargar un corte en Excel",
      description =
          "El mismo Excel de cobertura, con las cifras del momento del corte. 404 si no existe;"
              + " un corte de otro departamento devuelve 403.")
  @ApiResponse(
      responseCode = "200",
      description = "Archivo .xlsx",
      content = @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary")))
  @ApiResponse(
      responseCode = "404",
      description = "El corte no existe",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(
      path = "/cuts/{id}/excel",
      produces = {XLSX, PROBLEM})
  public ResponseEntity<byte[]> cutExcel(
      @PathVariable("id") String id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    CoverageCut cut =
        cutService
            .find(access, id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El corte no existe."));
    byte[] content = xlsxWriter.write(CoverageExcel.sheets(cut.report()));
    return excelResponse(content, "corte-cobertura-" + cut.id() + ".xlsx");
  }

  private CoverageReport buildCoverage(
      ReportSurvey reportSurvey, Integer year, Integer departmentCode, Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    int reportYear = year == null ? survey.year() : year;
    return coverageService.coverage(access, reportSurvey, reportYear, departmentCode);
  }

  /** Nombre {@code <reporte>-<encuesta>-<anio>[-dpto<NN>]-<yyyy-MM-dd>.xlsx}. */
  public static String fileName(
      String report, ReportSurvey reportSurvey, int year, Integer departmentCode, LocalDate day) {
    StringBuilder name = new StringBuilder(report);
    name.append('-').append(reportSurvey.name().toLowerCase(Locale.ROOT));
    name.append('-').append(year);
    if (departmentCode != null) {
      name.append("-dpto").append(String.format(Locale.ROOT, "%02d", departmentCode));
    }
    return name.append('-').append(day).append(".xlsx").toString();
  }

  /** Respuesta de descarga de Excel: adjunto, sin caché (puede traer datos personales). */
  public static ResponseEntity<byte[]> excelResponse(byte[] content, String fileName) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(XLSX))
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(fileName).build().toString())
        .body(content);
  }
}
