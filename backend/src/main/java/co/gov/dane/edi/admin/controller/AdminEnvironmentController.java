package co.gov.dane.edi.admin.controller;

import co.gov.dane.edi.admin.QuestionnaireWritesProperties;
import co.gov.dane.edi.admin.dto.AdminEnvironment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/environment")
@Tag(name = "Ambiente de administración", description = "Modo del ambiente (administradores)")
public class AdminEnvironmentController {

  private final QuestionnaireWritesProperties questionnaireWrites;

  public AdminEnvironmentController(QuestionnaireWritesProperties questionnaireWrites) {
    this.questionnaireWrites = questionnaireWrites;
  }

  @Operation(
      operationId = "adminEnvironment",
      summary = "Consultar si este ambiente permite cambiar el cuestionario",
      description =
          "En producción el cuestionario es de solo lectura: los cambios se hacen en QA y llegan"
              + " con un paquete de promoción. El front lo usa para deshabilitar los controles de"
              + " escritura; el backend igual rechaza esas operaciones con 403.")
  @ApiResponse(responseCode = "200", description = "Modo del ambiente")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminEnvironment environment() {
    return new AdminEnvironment(questionnaireWrites.questionnaireWritesEnabled());
  }
}
