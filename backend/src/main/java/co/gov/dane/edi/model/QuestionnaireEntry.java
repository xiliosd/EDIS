package co.gov.dane.edi.model;

import java.util.List;

/** Pregunta o enunciado tal como lo ve el encuestado; {@code help} son sus ayudas en orden. */
public record QuestionnaireEntry(
    long id,
    String pageCode,
    EntryKind kind,
    String numeral,
    String label,
    String text,
    List<SectionHeader> headers,
    AnswerSpec answer,
    LegacySkip legacySkip,
    List<TermHelp> help) {}
