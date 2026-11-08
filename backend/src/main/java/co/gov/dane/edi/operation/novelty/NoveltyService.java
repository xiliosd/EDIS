package co.gov.dane.edi.operation.novelty;

import co.gov.dane.edi.admin.AdminAudit;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationProperties;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.novelty.NoveltyRepository.CurrentNovelty;
import co.gov.dane.edi.operation.novelty.dto.NoveltyCatalogEntry;
import co.gov.dane.edi.operation.novelty.dto.NoveltyChangeRequest;
import co.gov.dane.edi.operation.novelty.dto.NoveltyEntity;
import co.gov.dane.edi.operation.novelty.dto.NoveltyPerson;
import co.gov.dane.edi.operation.novelty.dto.NoveltyRemoveRequest;
import co.gov.dane.edi.operation.report.ReportChapterTables;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.InvalidEditException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import co.gov.dane.edi.util.Latin9Text;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de novedades por la territorial (diseño §6): asignar, modificar y quitar la novedad de un
 * funcionario de sus departamentos. Maneja datos personales: no escribe nada en logs salvo la línea
 * de auditoría, que solo lleva códigos (IDMUES y novedades), nunca cédula, nombre ni causa.
 */
@Service
public class NoveltyService {

  /** Bytes de NOV_OTRA_CAUSA en la base real (Latin-9, un byte por carácter). */
  static final int OTHER_CAUSE_MAX_BYTES = 500;

  static final String ASSIGN_OPERATION = "novelty-assign";
  static final String REMOVE_OPERATION = "novelty-remove";
  static final String NO_SAMPLE_ID = "sin-idmues";
  static final String NO_NOVELTY = "ninguna";

  /** Mismo texto para "no existe" y "es de otro departamento": no se revela cuál de los dos. */
  static final String NOT_FOUND_MESSAGE =
      "El funcionario no está en la muestra de sus departamentos.";

  static final String CONFLICT_MESSAGE =
      "La novedad del funcionario cambió desde que se cargó la pantalla. Recargue la lista.";

  private final NoveltyRepository repository;
  private final ReportChapterTables chapterTables;
  private final OperationProperties operationProperties;
  private final SurveyProperties survey;
  private final AdminAudit audit;

  public NoveltyService(
      NoveltyRepository repository,
      ReportChapterTables chapterTables,
      OperationProperties operationProperties,
      SurveyProperties survey,
      AdminAudit audit) {
    this.repository = repository;
    this.chapterTables = chapterTables;
    this.operationProperties = operationProperties;
    this.survey = survey;
    this.audit = audit;
  }

  public List<NoveltyCatalogEntry> catalog(OperationalAccess access, int year) {
    requireNoveltyManagement(access);
    int otherCauseCode = operationProperties.noveltyOtherCauseCode();
    return repository.findCatalog(year).stream()
        .map(row -> new NoveltyCatalogEntry(row.code(), row.name(), row.code() == otherCauseCode))
        .toList();
  }

  public List<NoveltyEntity> entities(OperationalAccess access, int year, int departmentCode) {
    requireNoveltyManagement(access);
    List<Integer> departments = access.departmentFilter(departmentCode);
    return repository.findEntities(year, departments).stream()
        .map(row -> new NoveltyEntity(row.code(), row.name(), row.sampleSize(), row.withNovelty()))
        .toList();
  }

  public List<NoveltyPerson> people(
      OperationalAccess access, int year, int departmentCode, long entityCode) {
    requireNoveltyManagement(access);
    List<Integer> departments = access.departmentFilter(departmentCode);
    return repository.findPeople(chapterTables.statusChapters(year), year, departments, entityCode);
  }

  /** Asigna o modifica la novedad; 422 si no es válida, 404 si no es visible, 409 si cambió. */
  @Transactional
  public void assign(OperationalAccess access, String username, NoveltyChangeRequest request) {
    requireNoveltyManagement(access);
    int year = request.anio();
    boolean inCatalog =
        repository.findCatalog(year).stream().anyMatch(row -> row.code() == request.noveltyCode());
    if (!inCatalog) {
      throw new InvalidEditException(
          "noveltyCode", "La novedad no existe en el catálogo de la encuesta para el año.");
    }
    String storedText = null;
    if (request.noveltyCode() == operationProperties.noveltyOtherCauseCode()) {
      storedText = storedOtherCause(request.otherCause());
    }
    change(
        access,
        username,
        ASSIGN_OPERATION,
        year,
        request.respondentCode(),
        request.noveltyCode(),
        storedText,
        request.expectedNoveltyCode(),
        request.expectedOtherCause());
  }

  /** Quita la novedad (IDFNOV y NOV_OTRA_CAUSA nulos); 404 si no es visible, 409 si cambió. */
  @Transactional
  public void remove(OperationalAccess access, String username, NoveltyRemoveRequest request) {
    requireNoveltyManagement(access);
    change(
        access,
        username,
        REMOVE_OPERATION,
        request.anio(),
        request.respondentCode(),
        null,
        null,
        request.expectedNoveltyCode(),
        request.expectedOtherCause());
  }

  private void change(
      OperationalAccess access,
      String username,
      String operation,
      int year,
      String respondentCode,
      Integer noveltyCode,
      String storedText,
      Integer expectedCode,
      String expectedText) {
    List<Integer> departments = access.departmentFilter(null);
    // El esperado se compara en su forma guardada, convertido igual que al escribir.
    String expectedStored =
        expectedText == null || expectedText.isEmpty() ? null : Latin9Text.encode(expectedText);
    int updated =
        repository.updateNovelty(
            year,
            respondentCode,
            departments,
            noveltyCode,
            storedText,
            expectedCode,
            expectedStored);
    CurrentNovelty current =
        repository
            .findCurrent(year, respondentCode, departments)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    if (updated == 0) {
      throw new ConflictException(CONFLICT_MESSAGE);
    }
    if (updated != 1) {
      // C_C y año deberían ser únicos: si no, se revierte en vez de tocar a varias personas.
      throw new IllegalStateException("La novedad afectó más de un funcionario; no se guardó.");
    }
    String sampleId =
        AdminAudit.isValidCode(current.sampleId()) ? current.sampleId() : NO_SAMPLE_ID;
    audit.record(
        username,
        survey.code(),
        year,
        operation,
        List.of(sampleId, auditCode(expectedCode), auditCode(noveltyCode)));
  }

  /** Causa obligatoria de "otra causa", convertida a Latin-9 y dentro del límite de la columna. */
  private static String storedOtherCause(String otherCause) {
    String text = otherCause == null ? "" : otherCause.strip();
    if (text.isEmpty()) {
      throw new InvalidEditException("otherCause", "Escriba la causa de la novedad.");
    }
    if (!Latin9Text.fits(text, OTHER_CAUSE_MAX_BYTES)) {
      throw new InvalidEditException(
          "otherCause",
          "La causa supera 500 caracteres (las comillas tipográficas, rayas y símbolos ocupan"
              + " varios).");
    }
    return Latin9Text.encode(text);
  }

  private static String auditCode(Integer noveltyCode) {
    return noveltyCode == null ? NO_NOVELTY : String.valueOf(noveltyCode);
  }

  private static void requireNoveltyManagement(OperationalAccess access) {
    if (!access.canManageNovelties()) {
      throw new AccessDeniedException("El usuario no gestiona novedades");
    }
  }
}
