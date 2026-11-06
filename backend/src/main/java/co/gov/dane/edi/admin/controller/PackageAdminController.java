package co.gov.dane.edi.admin.controller;

import co.gov.dane.edi.admin.AllowedInReadOnlyQuestionnaire;
import co.gov.dane.edi.admin.PackageImportService;
import co.gov.dane.edi.admin.PackageKind;
import co.gov.dane.edi.admin.PromotionPackageService;
import co.gov.dane.edi.admin.PromotionPackageService.PackageFile;
import co.gov.dane.edi.admin.dto.ImportPreview;
import co.gov.dane.edi.security.SessionTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Arrays;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/questionnaire/{encuesta}/{anio}/package")
@Tag(
    name = "Paquetes de promoción",
    description =
        "Paquete verificable de un año publicado: se genera en QA y se importa en producción (rol"
            + " ADMIN_CUESTIONARIO)")
public class PackageAdminController {

  private static final String PROBLEM = "application/problem+json";
  private static final String ZIP = "application/zip";

  private final PromotionPackageService packages;
  private final PackageImportService imports;
  private final SessionTokenService tokens;

  public PackageAdminController(
      PromotionPackageService packages, PackageImportService imports, SessionTokenService tokens) {
    this.packages = packages;
    this.imports = imports;
    this.tokens = tokens;
  }

  @Operation(
      operationId = "packageDownload",
      summary = "Descargar el paquete de promoción de un año publicado",
      description =
          "ZIP con manifiesto.json, reglas.json, frm.sql y columnas.sql. El tipo anio instala el"
              + " año completo; correccion exige una versión posterior a la publicación y además"
              + " corrige los textos de las filas que ya existen. La persona técnica ejecuta"
              + " columnas.sql y frm.sql en producción antes de importarlo.")
  @ApiResponse(
      responseCode = "200",
      description = "Paquete del año",
      content = @Content(mediaType = ZIP, schema = @Schema(type = "string", format = "binary")))
  @ApiResponse(
      responseCode = "400",
      description = "Falta el tipo o no es anio ni correccion",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año no existen",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "El año no está publicado o no hay correcciones posteriores a la publicación",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "El año publicado ya no pasa la lista de publicación (propiedad checklist)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(produces = {ZIP, PROBLEM})
  public ResponseEntity<byte[]> download(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @Parameter(
              description = "Tipo de paquete",
              schema = @Schema(allowableValues = {"anio", "correccion"}))
          @RequestParam("tipo")
          String kind,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    PackageFile file = packages.build(survey, year, kindOf(kind), user(jwt));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(ZIP))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .body(file.content());
  }

  @Operation(
      operationId = "packageImport",
      summary = "Revisar o confirmar la importación de un paquete",
      description =
          "Comprueba el ZIP contra su manifiesto y contra esta base: los archivos, las reglas, las"
              + " filas FRM_* (que frm.sql ya debe haber dejado), las columnas y tablas de"
              + " respuesta y la versión. Con confirmar=false solo muestra el impacto: cuántos"
              + " encuestados ya guardaron cada página cuyas reglas cambian. Con confirmar=true"
              + " además reemplaza el archivo de reglas conservando la versión del paquete. Nunca"
              + " escribe en FRM_*.")
  @ApiResponse(responseCode = "200", description = "Impacto de la importación")
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta no existe",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "La versión del paquete no es posterior a la de este ambiente",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "413",
      description = "El paquete supera 5 MB",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description =
          "El paquete no pasa una revisión; la propiedad differences lista lo que no coincide",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  // Único cambio permitido en solo lectura: así recibe producción lo que se aprobó en QA.
  @AllowedInReadOnlyQuestionnaire
  @PostMapping(
      path = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ImportPreview importPackage(
      @PathVariable("encuesta") String survey,
      @PathVariable("anio") int year,
      @RequestPart("archivo") MultipartFile file,
      @RequestParam(name = "confirmar", defaultValue = "false") boolean confirm,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt)
      throws IOException {
    return imports.importPackage(survey, year, file.getBytes(), confirm, user(jwt));
  }

  private static PackageKind kindOf(String value) {
    return Arrays.stream(PackageKind.values())
        .filter(kind -> kind.jsonValue().equals(value))
        .findFirst()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El tipo de paquete debe ser anio o correccion."));
  }

  private String user(Jwt jwt) {
    return tokens.principalFrom(jwt).subject();
  }
}
