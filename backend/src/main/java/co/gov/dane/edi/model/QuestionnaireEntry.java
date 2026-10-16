package co.gov.dane.edi.model;

import java.util.List;

public record QuestionnaireEntry(
    long id,
    String pageCode,
    EntryKind kind,
    String numeral,
    String label,
    String text,
    List<SectionHeader> headers,
    AnswerSpec answer,
    LegacySkip legacySkip) {}
