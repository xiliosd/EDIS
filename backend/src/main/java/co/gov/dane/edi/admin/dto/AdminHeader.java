package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.model.HeaderKind;

/** Encabezado de una pregunta con su clave en FRM_ENCABEZADO, para poder editarlo. */
public record AdminHeader(long codEnc, HeaderKind kind, String text) {}
