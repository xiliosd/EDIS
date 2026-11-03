package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.model.AnswerOption;
import co.gov.dane.edi.model.ColumnStatus;
import java.util.List;

/**
 * Pregunta que el editor de reglas ofrece como condición o destino. {@code text} es la vista previa
 * en texto plano, la misma de {@code AdminEntry.text}.
 */
public record RuleQuestion(
    String column,
    String pageCode,
    String numeral,
    String text,
    String inputType,
    List<AnswerOption> options,
    ColumnStatus columnStatus) {}
