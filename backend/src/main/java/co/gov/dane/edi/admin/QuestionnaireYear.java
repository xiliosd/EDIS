package co.gov.dane.edi.admin;

import co.gov.dane.edi.rules.RuleSet;

/** Resumen de un año del cuestionario tal como lo describe su archivo de reglas. */
public record QuestionnaireYear(String survey, int year, YearState state, int version) {

  public static QuestionnaireYear from(RuleSet rules) {
    return new QuestionnaireYear(
        rules.survey(), rules.year(), rules.effectiveState(), rules.version());
  }
}
