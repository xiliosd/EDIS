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

  /**
   * Sin departamento pedido: los del usuario. Quien tiene {@link #allDepartments()} no debe filtrar
   * por departamento (esta lista es solo el catálogo y puede estar incompleta).
   */
  public List<Integer> departmentCodesOrAll(Integer requested) {
    if (!allDepartments && departments.isEmpty()) {
      // Una lista vacía nunca significa "todos": sin departamentos asignados no hay acceso.
      throw new AccessDeniedException("El usuario no tiene departamentos asignados");
    }
    if (requested == null) {
      return departments.stream().map(Department::code).toList();
    }
    requireDepartment(requested);
    return List.of(requested);
  }

  /**
   * Filtro de departamentos para una consulta: nulo (sin filtro) si el usuario ve todo el país y no
   * pidió departamento (el catálogo puede no tenerlos todos); si no, {@link
   * #departmentCodesOrAll(Integer)}.
   */
  public List<Integer> departmentFilter(Integer requested) {
    return allDepartments && requested == null ? null : departmentCodesOrAll(requested);
  }
}
