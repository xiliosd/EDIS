package co.gov.dane.edi.admin.dto;

import co.gov.dane.edi.rules.TestCase;
import jakarta.validation.constraints.NotNull;

/** Un caso de prueba y la versión del archivo del año que el cliente leyó. */
public record TestCaseRequest(
    @NotNull(message = "Falta el contenido.") TestCase testCase,
    @NotNull(message = "Falta la versión que leyó.") Integer version) {}
