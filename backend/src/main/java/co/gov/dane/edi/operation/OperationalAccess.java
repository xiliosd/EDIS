package co.gov.dane.edi.operation;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;

/**
 * Alcance operativo de un administrador: los departamentos que puede consultar y qué gestiona. Un
 * departamento ajeno es un 403, no un resultado vacío, para no ocultar un uso indebido.
 */
public record OperationalAccess(
    boolean allDepartments,
    List<Department> departments,
    boolean canManageNovelties,
    boolean canManageKeys) {

  public OperationalAccess {
    departments = List.copyOf(departments);
  }

  public void requireDepartment(int departmentCode) {
    if (!allDepartments && departments.stream().noneMatch(d -> d.code() == departmentCode)) {
      throw new AccessDeniedException("Departamento fuera del alcance del usuario");
    }
  }

  /** Sin departamento pedido: los del usuario (todos si es nacional u operativo). */
  public List<Integer> departmentCodesOrAll(Integer requested) {
    if (requested == null) {
      return departments.stream().map(Department::code).toList();
    }
    requireDepartment(requested);
    return List.of(requested);
  }
}
