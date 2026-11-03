package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.model.PageDetail;
import co.gov.dane.edi.model.RespondentRules;
import java.util.List;

/**
 * Lo que necesita el simulador del administrador: las reglas efectivas del año y sus páginas con
 * las preguntas activas y pendientes, sin estado ni respuestas (nunca lee tablas de respuestas).
 */
public record SimulatorView(int version, RespondentRules rules, List<PageDetail> pages) {}
