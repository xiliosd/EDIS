package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.admin.YearState;
import co.gov.dane.edi.rules.HistoryEntry;
import java.util.List;

/** Historial de versiones de un año: la vigente, su estado y las entradas, de nueva a antigua. */
public record HistoryView(int version, YearState state, List<HistoryEntry> entries) {}
