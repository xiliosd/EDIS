package co.gov.dane.edi.operation.report;

import co.gov.dane.edi.admin.AdminAudit;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.OperationalTime;
import co.gov.dane.edi.operation.report.dto.CoverageReport;
import co.gov.dane.edi.operation.report.dto.CutRequest;
import co.gov.dane.edi.operation.report.dto.CutSummary;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Genera cortes de un departamento, los lista y los entrega según el alcance del usuario. */
@Service
public class CoverageCutService {

  private static final DateTimeFormatter ID_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final int RANDOM_BYTES = 3;
  private static final String AUDIT_OPERATION = "report-cut-create";

  private final CoverageService coverageService;
  private final CoverageCutStore store;
  private final SurveyProperties survey;
  private final AdminAudit audit;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public CoverageCutService(
      CoverageService coverageService,
      CoverageCutStore store,
      SurveyProperties survey,
      AdminAudit audit,
      Clock clock) {
    this.coverageService = coverageService;
    this.store = store;
    this.survey = survey;
    this.audit = audit;
    this.clock = clock;
  }

  /** Un departamento fuera del alcance es 403 (lo lanza el cálculo de cobertura). */
  public CutSummary create(OperationalAccess access, CutRequest request, String username) {
    int year = request.anio() == null ? survey.year() : request.anio();
    int departmentCode = request.departamento();
    CoverageReport report =
        coverageService.coverage(access, request.encuesta(), year, departmentCode);
    // Ruling 6: el id y la fecha visibles van en hora de Bogotá, no en UTC.
    OffsetDateTime now = OperationalTime.now(clock);
    CoverageCut cut =
        new CoverageCut(
            newId(now), request.encuesta(), year, departmentCode, username, now, report);
    store.save(cut);
    audit.record(
        username,
        survey.code(),
        year,
        AUDIT_OPERATION,
        List.of(String.valueOf(departmentCode), cut.id()));
    return cut.summary();
  }

  /** Sin departamento pedido: todos los del usuario (nacional y operativo no se filtran). */
  public List<CutSummary> list(OperationalAccess access, int year, Integer departmentCode) {
    if (access.allDepartments() && departmentCode == null) {
      return store.list(year, null);
    }
    return store.list(year, access.departmentCodesOrAll(departmentCode));
  }

  /** Vacío si no existe o el id es inválido (404); 403 si el departamento no es del usuario. */
  public Optional<CoverageCut> find(OperationalAccess access, String id) {
    Optional<CoverageCut> cut = store.find(id);
    cut.ifPresent(found -> access.requireDepartment(found.departmentCode()));
    return cut;
  }

  private String newId(OffsetDateTime now) {
    byte[] bytes = new byte[RANDOM_BYTES];
    random.nextBytes(bytes);
    return ID_STAMP.format(now) + "-" + HexFormat.of().formatHex(bytes);
  }
}
