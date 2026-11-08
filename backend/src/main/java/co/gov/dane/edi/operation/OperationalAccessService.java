package co.gov.dane.edi.operation;

import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Resuelve en cada petición los departamentos del administrador (y no en el token) para que un
 * cambio en GRAL_TERRITORIAL_ACCESO aplique sin cerrar sesión; se cachea 60 s por usuario.
 */
@Service
public class OperationalAccessService {

  static final Duration CACHE_TTL = Duration.ofSeconds(60);

  private record CacheKey(String subject, Set<Role> roles, String departmentCode) {}

  private record CacheEntry(OperationalAccess access, long expiresAtNanos) {}

  private final DepartmentAccessRepository departments;
  private final SessionTokenService tokens;
  private final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

  public OperationalAccessService(
      DepartmentAccessRepository departments, SessionTokenService tokens) {
    this.departments = departments;
    this.tokens = tokens;
  }

  public OperationalAccess current(Jwt jwt) {
    return accessFor(tokens.principalFrom(jwt));
  }

  public OperationalAccess accessFor(SessionPrincipal principal) {
    CacheKey key = new CacheKey(principal.subject(), principal.roles(), principal.departmentCode());
    long now = System.nanoTime();
    CacheEntry cached = cache.get(key);
    if (cached != null && now - cached.expiresAtNanos() < 0) {
      return cached.access();
    }
    OperationalAccess access = resolve(principal);
    cache.put(key, new CacheEntry(access, now + CACHE_TTL.toNanos()));
    return access;
  }

  /** Para pruebas y cambios masivos de GRAL_TERRITORIAL_ACCESO. */
  public void clearCache() {
    cache.clear();
  }

  private OperationalAccess resolve(SessionPrincipal principal) {
    boolean national = principal.roles().contains(Role.ADMIN_NACIONAL);
    boolean operative = principal.roles().contains(Role.ADMIN_OPERATIVO);
    boolean territorial = principal.roles().contains(Role.ADMIN_TERRITORIAL);
    if (national || operative) {
      // Ruling 8: gestionar novedades es solo sobre departamentos explícitos; con alcance completo
      // no se gestiona aunque el usuario también sea territorial.
      return new OperationalAccess(true, departments.findAll(), false, national);
    }
    if (territorial) {
      return new OperationalAccess(false, territorialDepartments(principal), true, false);
    }
    return new OperationalAccess(false, List.of(), false, false);
  }

  private List<Department> territorialDepartments(SessionPrincipal principal) {
    List<Department> assigned = departments.findAssignedTo(principal.administratorUsername());
    if (!assigned.isEmpty()) {
      return assigned;
    }
    return homeDepartment(principal.departmentCode());
  }

  private List<Department> homeDepartment(String departmentCode) {
    if (departmentCode == null || !departmentCode.trim().matches("\\d{1,3}")) {
      return List.of();
    }
    return departments.findByCode(Integer.parseInt(departmentCode.trim()));
  }
}
