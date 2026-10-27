package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.model.ColumnStatus;
import java.util.List;

/**
 * Pregunta o enunciado tal como lo ve el editor. {@code text} es texto plano (sin entidades HTML);
 * {@code referencedByRules} lista los ids de las reglas que mencionan su columna.
 */
public record AdminEntry(
    long capProyId,
    String numeral,
    String columnCode,
    String kind,
    String inputType,
    String text,
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
        columnStatus,
        optionGroup,
        headers,
        help,
        List.copyOf(ruleIds));
  }
}
