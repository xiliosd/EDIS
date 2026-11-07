package co.gov.dane.edi.model;

import java.util.List;

/** Encabezado o subtítulo de una pregunta, con las ayudas de FRM_ENC_AYUDA en su orden. */
public record SectionHeader(HeaderKind kind, String text, List<TermHelp> help) {}
