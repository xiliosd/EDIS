package co.gov.dane.edi.rules;

import java.util.SortedMap;
import java.util.SortedSet;

/** Resultado del motor: columnas deshabilitadas y errores (columna → código) de las habilitadas. */
public record Evaluation(SortedSet<String> disabled, SortedMap<String, String> errors) {}
