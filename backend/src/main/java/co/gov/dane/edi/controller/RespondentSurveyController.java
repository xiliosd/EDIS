package co.gov.dane.edi.controller;

import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/survey")
@Tag(name = "Encuesta", description = "Diligenciamiento del cuestionario")
public class RespondentSurveyController {

  private final EffectiveRulesService rules;

  public RespondentSurveyController(EffectiveRulesService rules) {
    this.rules = rules;
  }

  @Operation(summary = "Consultar las reglas de saltos y validaciones vigentes")
  @ApiResponse(responseCode = "200", description = "Reglas del archivo más saltos heredados")
  @GetMapping(value = "/rules", produces = MediaType.APPLICATION_JSON_VALUE)
  public RuleSet rules() {
    return rules.effectiveRules();
  }
}
