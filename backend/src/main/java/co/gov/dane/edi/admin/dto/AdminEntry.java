package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.model.ColumnStatus;
import java.util.List;

/**
 * Pregunta o enunciado tal como lo ve el editor. {@code text} es la vista previa en texto plano
 * (con el año ya sustituido en el comodín 200X) y {@code rawText} el texto guardado en texto plano
 * sin esa sustitución: es el que se edita; {@code referencedByRules} lista los ids de las reglas
 * que mencionan su columna.
 */
public record AdminEntry(
    long capProyId,
    String numeral,
    String columnCode,
    String kind,
    String inputType,
    String text,
    String rawText,
    ColumnStatus columnStatus,
    Integer optionGroup,
    List<AdminHeader> headers,
    AdminHelp help,
    List<String> referencedByRules) {

  public AdminEntry withReferencedByRules(List<String> ruleIds) {
    return new AdminEntry(
        capProyId,
        numeral,
        columnCode,
        kind,
        inputType,
        text,
        rawText,
        columnStatus,
        optionGroup,
        headers,
        help,
        List.copyOf(ruleIds));
  }
}
