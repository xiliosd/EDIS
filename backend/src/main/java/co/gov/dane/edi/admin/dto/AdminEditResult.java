package co.gov.dane.edi.admin.dto;

import java.util.List;

/**
 * Página ya actualizada tras una edición y la nueva versión del año. {@code
 * skipsTargetingEarlierQuestions}: ids de reglas de salto cuyo destino queda antes de la pregunta
 * que las dispara (solo al reordenar; vacío en las demás ediciones).
 */
public record AdminEditResult(
    int version, AdminPage page, List<String> skipsTargetingEarlierQuestions) {}
