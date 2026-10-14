package co.gov.dane.edi.model;

import java.util.List;

public record AnswerSpec(
    InputType inputType,
    String columnCode,
    ColumnStatus columnStatus,
    List<AnswerOption> options) {}
