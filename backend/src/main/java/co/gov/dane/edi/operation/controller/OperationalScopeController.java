package co.gov.dane.edi.operation.controller;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.Department;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.OperationalAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/operation")
@Tag(name = "Operación", description = "Alcance operativo del administrador")
public class OperationalScopeController {

  /** Departamentos y permisos de gestión del usuario, más el año por defecto de los reportes. */
  public record OperationalScope(
      boolean allDepartments,
      List<Department> departments,
      boolean canManageNovelties,
      boolean canManageKeys,
      int defaultYear) {}

  private final OperationalAccessService accessService;
  private final SurveyProperties survey;

  public OperationalScopeController(
      OperationalAccessService accessService, SurveyProperties survey) {
    this.accessService = accessService;
    this.survey = survey;
  }

  @Operation(
      operationId = "operationScope",
      summary = "Consultar el alcance operativo del usuario",
      description =
          "Nacional y operativo ven todos los departamentos; el territorial, solo los asignados."
              + " El administrador del cuestionario recibe 403.")
  @ApiResponse(responseCode = "200", description = "Alcance del usuario")
  @GetMapping(value = "/scope", produces = MediaType.APPLICATION_JSON_VALUE)
  public OperationalScope scope(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    OperationalAccess access = accessService.current(jwt);
    return new OperationalScope(
        access.allDepartments(),
        access.departments(),
        access.canManageNovelties(),
        access.canManageKeys(),
        survey.year());
  }
}
