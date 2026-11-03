package co.gov.dane.edi.admin.controller;

import co.gov.dane.edi.admin.RulesAdminService;
import co.gov.dane.edi.admin.dto.SimulatorView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/questionnaire/{encuesta}/{anio}/simulator")
@Tag(
    name = "Simulador de reglas",
    description = "Reglas y páginas de un año para probarlas sin respuestas (ADMIN_CUESTIONARIO)")
public class SimulatorAdminController {

  private static final String PROBLEM = "application/problem+json";

  private final RulesAdminService rules;

  public SimulatorAdminController(RulesAdminService rules) {
    this.rules = rules;
  }

  @Operation(
      operationId = "simulatorView",
      summary = "Consultar lo que necesita el simulador de un año",
      description =
          "Devuelve las reglas efectivas y las páginas con sus preguntas, sin estado ni"
              + " respuestas: nunca lee ni escribe tablas de respuestas. También responde para"
              + " años publicados, en modo de solo lectura.")
  @ApiResponse(responseCode = "200", description = "Reglas efectivas y páginas del año")
  @ApiResponse(
      responseCode = "404",
      description = "La encuesta o el año no existen",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public SimulatorView view(
      @PathVariable("encuesta") String survey, @PathVariable("anio") int year) {
    return rules.simulator(survey, year);
  }
}
