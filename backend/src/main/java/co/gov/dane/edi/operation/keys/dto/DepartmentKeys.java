package co.gov.dane.edi.operation.keys.dto;

/**
 * Funcionarios elegibles para clave en un departamento ({@code TC1 = 1} y {@code ATV = 1}, como el
 * login), cuántos ya tienen usuario y cuántos no.
 */
public record DepartmentKeys(int code, String name, long eligible, long withKey, long withoutKey) {}
