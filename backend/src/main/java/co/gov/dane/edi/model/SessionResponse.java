package co.gov.dane.edi.model;

import java.util.List;

/** Lo que el front necesita de la sesión. No incluye C_C, nombres ni claves. */
public record SessionResponse(
    AccountType accountType,
    List<String> roles,
    String surveyCode,
    int surveyYear,
    String departmentCode) {}
