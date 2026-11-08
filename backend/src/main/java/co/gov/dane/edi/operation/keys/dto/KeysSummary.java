package co.gov.dane.edi.operation.keys.dto;

import java.util.List;

/** Resumen de claves de la muestra del año, por departamento. */
public record KeysSummary(int year, List<DepartmentKeys> departments) {

  public KeysSummary {
    departments = List.copyOf(departments);
  }
}
