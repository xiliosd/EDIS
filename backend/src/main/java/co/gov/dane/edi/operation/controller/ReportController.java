package co.gov.dane.edi.operation.controller;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.OperationalAccessService;
import co.gov.dane.edi.operation.excel.XlsxWriter;
import co.gov.dane.edi.operation.report.CoverageExcel;
import co.gov.dane.edi.operation.report.CoverageService;
import co.gov.dane.edi.operation.report.ReportSurvey;
import co.gov.dane.edi.operation.report.ReportYearsRepository;
import co.gov.dane.edi.operation.report.dto.CoverageReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
@Tag(name = "Reportes", description = "Reportes operativos de la muestra (consulta y Excel)")
public class ReportController {

  public static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final String PROBLEM = "application/problem+json";

  private final OperationalAccessService accessService;
  private final CoverageService coverageService;
  private final ReportYearsRepository yearsRepository;
  private final XlsxWriter xlsxWriter;
  private final SurveyProperties survey;
  private final Clock clock;

  public ReportController(
      OperationalAccessService accessService,
      CoverageService coverageService,
      ReportYearsRepository yearsRepository,
      XlsxWriter xlsxWriter,
      SurveyProperties survey,
      Clock clock) {
    this.accessService = accessService;
    this.coverageService = coverageService;
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
