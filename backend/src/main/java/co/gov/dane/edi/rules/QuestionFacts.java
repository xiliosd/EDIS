package co.gov.dane.edi.rules;

import co.gov.dane.edi.model.AnswerColumn;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.InputType;
import java.util.List;

/**
 * Lo que el analizador necesita saber de una pregunta del año: dónde está, su tipo y opciones, y la
 * capacidad de su columna. {@code position} es el orden (desde 0) en todo el año. {@code capacity}
 * es nulo cuando la columna aún no existe.
 */
public record QuestionFacts(
    String column,
    String pageCode,
    int position,
    InputType inputType,
    List<String> optionValues,
    ColumnStatus columnStatus,
    AnswerColumn capacity) {

  public QuestionFacts {
    optionValues = optionValues == null ? List.of() : List.copyOf(optionValues);
  }
}
