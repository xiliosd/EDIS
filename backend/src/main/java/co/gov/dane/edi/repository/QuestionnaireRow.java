package co.gov.dane.edi.repository;

/** Fila cruda del cuestionario tal como la arma la consulta heredada de Capitulos_Body.php. */
public record QuestionnaireRow(
    long questionId,
    long entryId,
    String text,
    String type,
    String numeral,
    Integer optionGroup,
    Integer legacySkipCount,
    String legacySkipValue,
    Integer objectCode) {}
