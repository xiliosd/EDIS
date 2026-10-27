package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.admin.YearState;
import java.util.List;
import java.util.Map;

/** Árbol completo del cuestionario de una encuesta y año; {@code tables}: capítulo a tabla. */
public record AdminQuestionnaireView(
    String survey,
    int year,
    YearState state,
    int version,
    Map<String, String> tables,
    List<AdminPage> pages) {}
