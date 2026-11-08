package co.gov.dane.edi.operation.keys;

import co.gov.dane.edi.admin.AdminAudit;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.keys.SampleKeysRepository.Assignment;
import co.gov.dane.edi.operation.keys.SampleKeysRepository.CredentialRow;
import co.gov.dane.edi.operation.keys.SampleKeysRepository.PendingRow;
import co.gov.dane.edi.operation.keys.dto.DepartmentKeys;
import co.gov.dane.edi.operation.keys.dto.KeysGenerateRequest;
import co.gov.dane.edi.operation.keys.dto.KeysGenerateResult;
import co.gov.dane.edi.operation.keys.dto.KeysSummary;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claves de la muestra (diseño §7), solo para el administrador nacional: resumen, generación para
 * quienes no tienen usuario y descarga por departamento. Nunca sobrescribe un usuario existente y
 * nunca escribe claves, usuarios, cédulas ni nombres en logs: la auditoría solo lleva la cantidad
 * generada o el código del departamento descargado.
 */
@Service
public class SampleKeysService {

  static final String GENERATE_OPERATION = "keys-generate";
  static final String DOWNLOAD_OPERATION = "keys-download";

  /**
   * Con 26^8 combinaciones un choque es rarísimo; el tope solo evita un ciclo infinito si el
   * generador fallara (por ejemplo, si devolviera siempre lo mismo).
   */
  static final int MAX_ATTEMPTS_PER_LOGIN = 1_000;

  static final String CONFLICT_MESSAGE =
      "El número de funcionarios sin clave cambió desde que se mostró la confirmación. Recargue"
          + " el resumen y confirme de nuevo.";

  static final String DEPARTMENT_NOT_FOUND_MESSAGE =
      "El departamento no tiene funcionarios elegibles para clave en la muestra del año.";

  private final SampleKeysRepository repository;
  private final CredentialGenerator generator;
  private final SurveyProperties survey;
  private final AdminAudit audit;

  public SampleKeysService(
      SampleKeysRepository repository,
      CredentialGenerator generator,
      SurveyProperties survey,
      AdminAudit audit) {
    this.repository = repository;
    this.generator = generator;
    this.survey = survey;
    this.audit = audit;
  }

  public KeysSummary summary(OperationalAccess access, int year) {
    requireKeyManagement(access);
    List<DepartmentKeys> departments =
        repository.findDepartments(year).stream()
            .map(
                row ->
                    new DepartmentKeys(
                        row.code(),
                        departmentName(row.code(), row.name()),
                        row.eligible(),
                        row.withKey(),
                        row.eligible() - row.withKey()))
            .toList();
    return new KeysSummary(year, departments);
  }

  /**
   * Asigna usuario y clave a los elegibles sin usuario del año, en una transacción: bloquea las
   * filas pendientes, verifica que sean las que la persona confirmó (409 si no) y genera usuarios
   * que no estén en uso en el año.
   */
  @Transactional
  public KeysGenerateResult generate(
      OperationalAccess access, String username, KeysGenerateRequest request) {
    requireKeyManagement(access);
    int year = request.anio();
    List<PendingRow> pending = repository.findPendingForUpdate(year);
    if (pending.size() != request.expectedWithoutKey()) {
      throw new ConflictException(CONFLICT_MESSAGE);
    }
    Set<String> usedLogins = repository.findLoginsOfYear(year);
    List<Assignment> assignments = new ArrayList<>(pending.size());
    for (PendingRow row : pending) {
      String login = unusedLogin(usedLogins);
      usedLogins.add(login);
      assignments.add(new Assignment(row, login, generator.next()));
    }
    if (!assignments.isEmpty()) {
      requireOneRowEach(repository.assign(year, assignments));
    }
    long generated = assignments.size();
    audit.record(
        username, survey.code(), year, GENERATE_OPERATION, List.of(String.valueOf(generated)));
    return new KeysGenerateResult(generated);
  }

  /**
   * Elegibles con usuario de un departamento, para la carta al coordinador; 404 si el departamento
   * no tiene elegibles en la muestra del año. Queda auditado quién descargó qué departamento.
   */
  public List<CredentialRow> departmentKeys(
      OperationalAccess access, String username, int year, int departmentCode) {
    requireKeyManagement(access);
    if (!repository.hasEligible(year, departmentCode)) {
      throw new ResourceNotFoundException(DEPARTMENT_NOT_FOUND_MESSAGE);
    }
    List<CredentialRow> rows = repository.findCredentials(year, departmentCode);
    audit.record(
        username, survey.code(), year, DOWNLOAD_OPERATION, List.of(String.valueOf(departmentCode)));
    return rows;
  }

  private String unusedLogin(Set<String> usedLogins) {
    for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_LOGIN; attempt++) {
      String candidate = generator.next();
      if (!usedLogins.contains(candidate)) {
        return candidate;
      }
    }
    // Sin el valor en el mensaje: podría ser un usuario real.
    throw new IllegalStateException("No se pudo generar un usuario libre; no se asignó ninguno.");
  }

  /**
   * Las filas siguen bloqueadas, así que cada UPDATE debe tocar exactamente una; si no, se revierte
   * toda la generación en vez de dejar claves a medias.
   */
  private static void requireOneRowEach(int[] counts) {
    for (int count : counts) {
      if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
        throw new IllegalStateException(
            "Una fila pendiente cambió durante la generación; no se asignó ninguna clave.");
      }
    }
  }

  private static String departmentName(int code, String name) {
    if (name != null && !name.isBlank()) {
      return name;
    }
    return code == 0 ? "Sin departamento" : "Departamento " + code;
  }

  private static void requireKeyManagement(OperationalAccess access) {
    if (!access.canManageKeys()) {
      throw new AccessDeniedException("El usuario no gestiona las claves de la muestra");
    }
  }
}
