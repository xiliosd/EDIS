package co.gov.dane.edi.operation.novelty;

import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_COMPLETE_WITH_NOVELTY;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_INACTIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NOT_STARTED;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NOVELTY_OTHER_CAUSE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NO_CATALOG_WITH_KEY;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_ANTIOQUIA;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_VALLE;
import static co.gov.dane.edi.support.OperationalSampleFixture.ENTITY_MIXED;
import static co.gov.dane.edi.support.OperationalSampleFixture.ENTITY_NO_CATALOG;
import static co.gov.dane.edi.support.OperationalSampleFixture.NOVELTY_COMMON;
import static co.gov.dane.edi.support.OperationalSampleFixture.NOVELTY_OTHER_CAUSE;
import static co.gov.dane.edi.support.OperationalSampleFixture.OTHER_CAUSE_TEXT;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_OPERATIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_TERRITORIAL_MULTI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.OperationalAccessService;
import co.gov.dane.edi.operation.novelty.dto.NoveltyCatalogEntry;
import co.gov.dane.edi.operation.novelty.dto.NoveltyChangeRequest;
import co.gov.dane.edi.operation.novelty.dto.NoveltyEntity;
import co.gov.dane.edi.operation.novelty.dto.NoveltyPerson;
import co.gov.dane.edi.operation.novelty.dto.NoveltyRemoveRequest;
import co.gov.dane.edi.operation.report.SampleStatus;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.InvalidEditException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import co.gov.dane.edi.support.OperationalSampleFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/**
 * Gestión de novedades sobre la muestra ficticia: reglas, Latin-9, concurrencia, alcance por
 * departamento y auditoría sin datos personales.
 */
@ExtendWith(OutputCaptureExtension.class)
class NoveltyServiceIT extends OracleIntegrationTest {

  /** Comillas tipográficas, raya y una marca de verificación: ninguna existe en Latin-9. */
  private static final String TYPOGRAPHIC_TEXT = "“Cambio de sede” — ✓";

  private static final String TYPOGRAPHIC_STORED = "&#8220;Cambio de sede&#8221; &#8212; &#10003;";

  /** Cédula del rango del fixture que no existe en la muestra. */
  private static final String MISSING_CASE = "900000098";

  @Autowired private NoveltyService service;
  @Autowired private OperationalAccessService accessService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SurveyProperties survey;

  private OperationalSampleFixture fixture;
  private OperationalAccess territorial;
  private int year;

  @BeforeEach
  void loadFixture() {
    fixture = new OperationalSampleFixture(jdbc);
    year = survey.year();
    fixture.load(year);
    accessService.clearCache();
    territorial =
        accessService.accessFor(
            SessionPrincipal.administrator(
                USER_TERRITORIAL_MULTI, EnumSet.of(Role.ADMIN_TERRITORIAL), "5"));
  }

  @AfterEach
  void clearFixture() {
    fixture.clear(year);
    accessService.clearCache();
  }

  @Test
  void assignsCommonNoveltyAndReadsIt() {
    service.assign(territorial, USER_TERRITORIAL_MULTI, change(CASE_NOT_STARTED, NOVELTY_COMMON));

    assertThat(stored(CASE_NOT_STARTED)).containsEntry("IDFNOV", 3).containsEntry("TEXT", null);
    NoveltyPerson person = person(CASE_NOT_STARTED);
    assertThat(person.noveltyCode()).isEqualTo(NOVELTY_COMMON);
    assertThat(person.otherCause()).isNull();
    assertThat(person.status()).isEqualTo(SampleStatus.NOVELTY);
    assertThat(person.name()).isEqualTo("Persona Ficticia 04");
    assertThat(person.level()).isEqualTo(1);
  }

  @Test
  void changesToOtherCauseStoringEntitiesAndReadsTheSameText() {
    service.assign(
        territorial,
        USER_TERRITORIAL_MULTI,
        new NoveltyChangeRequest(
            year,
            CASE_COMPLETE_WITH_NOVELTY,
            NOVELTY_OTHER_CAUSE,
            "  " + TYPOGRAPHIC_TEXT + " ",
            NOVELTY_COMMON,
            null));

    assertThat(stored(CASE_COMPLETE_WITH_NOVELTY))
        .containsEntry("IDFNOV", 8)
        .containsEntry("TEXT", TYPOGRAPHIC_STORED);
    NoveltyPerson person = person(CASE_COMPLETE_WITH_NOVELTY);
    assertThat(person.noveltyCode()).isEqualTo(NOVELTY_OTHER_CAUSE);
    assertThat(person.otherCause()).isEqualTo(TYPOGRAPHIC_TEXT);
    // Lo completo prevalece sobre la novedad.
    assertThat(person.status()).isEqualTo(SampleStatus.COMPLETE);

    // La pantalla envía como esperado el texto que leyó: debe coincidir con el guardado.
    service.remove(
        territorial,
        USER_TERRITORIAL_MULTI,
        new NoveltyRemoveRequest(
            year, CASE_COMPLETE_WITH_NOVELTY, NOVELTY_OTHER_CAUSE, person.otherCause()));
    assertThat(stored(CASE_COMPLETE_WITH_NOVELTY))
        .containsEntry("IDFNOV", null)
        .containsEntry("TEXT", null);
  }

  @Test
  void otherCauseOfExactly500Latin9BytesFitsAnd501IsRejected() {
    // "“" ocupa 7 bytes ya convertido (&#8220;). Relleno ASCII: la base de pruebas es AL32UTF8 y
    // una "ñ" ocuparía allí dos bytes de la columna, a diferencia de la base real (Latin-9).
    String exact = "“" + "a".repeat(493);
    service.assign(
        territorial,
        USER_TERRITORIAL_MULTI,
        new NoveltyChangeRequest(year, CASE_NOT_STARTED, NOVELTY_OTHER_CAUSE, exact, null, null));
    assertThat(person(CASE_NOT_STARTED).otherCause()).isEqualTo(exact);

    String tooLong = exact + "a";
    assertThatThrownBy(
            () ->
                service.assign(
                    territorial,
                    USER_TERRITORIAL_MULTI,
                    new NoveltyChangeRequest(
                        year,
                        CASE_NOT_STARTED,
                        NOVELTY_OTHER_CAUSE,
                        tooLong,
                        NOVELTY_OTHER_CAUSE,
                        exact)))
        .isInstanceOf(InvalidEditException.class)
        .extracting("field")
        .isEqualTo("otherCause");
    assertThat(person(CASE_NOT_STARTED).otherCause()).isEqualTo(exact);
  }

  @Test
  void unknownCodeIsRejected() {
    assertThatThrownBy(
            () ->
                service.assign(
                    territorial,
                    USER_TERRITORIAL_MULTI,
                    new NoveltyChangeRequest(year, CASE_NOT_STARTED, 99, null, null, null)))
        .isInstanceOf(InvalidEditException.class)
        .extracting("field")
        .isEqualTo("noveltyCode");
    // El catálogo es por año: el código 3 de 2026 no vale para otro año.
    assertThatThrownBy(
            () ->
                service.assign(
                    territorial,
                    USER_TERRITORIAL_MULTI,
                    new NoveltyChangeRequest(
                        year - 1, CASE_NOT_STARTED, NOVELTY_COMMON, null, null, null)))
        .isInstanceOf(InvalidEditException.class);
    assertThat(stored(CASE_NOT_STARTED)).containsEntry("IDFNOV", null);
  }

  @Test
  void otherCauseWithoutTextIsRejected() {
    for (String text : new String[] {null, "", "   "}) {
      assertThatThrownBy(
              () ->
                  service.assign(
                      territorial,
                      USER_TERRITORIAL_MULTI,
                      new NoveltyChangeRequest(
                          year, CASE_NOT_STARTED, NOVELTY_OTHER_CAUSE, text, null, null)))
          .isInstanceOf(InvalidEditException.class)
          .extracting("field")
          .isEqualTo("otherCause");
    }
    assertThat(stored(CASE_NOT_STARTED)).containsEntry("IDFNOV", null);
  }

  @Test
  void commonNoveltyDiscardsAnyText() {
    service.assign(
        territorial,
        USER_TERRITORIAL_MULTI,
        new NoveltyChangeRequest(
            year,
            CASE_NOVELTY_OTHER_CAUSE,
            NOVELTY_COMMON,
            "texto que no aplica",
            NOVELTY_OTHER_CAUSE,
            OTHER_CAUSE_TEXT));
    assertThat(stored(CASE_NOVELTY_OTHER_CAUSE))
        .containsEntry("IDFNOV", 3)
        .containsEntry("TEXT", null);
  }

  @Test
  void removeClearsBothColumns() {
    service.remove(
        territorial,
        USER_TERRITORIAL_MULTI,
        new NoveltyRemoveRequest(
            year, CASE_NOVELTY_OTHER_CAUSE, NOVELTY_OTHER_CAUSE, OTHER_CAUSE_TEXT));
    assertThat(stored(CASE_NOVELTY_OTHER_CAUSE))
        .containsEntry("IDFNOV", null)
        .containsEntry("TEXT", null);
    assertThat(person(CASE_NOVELTY_OTHER_CAUSE).status()).isEqualTo(SampleStatus.NOT_STARTED);
  }

  @Test
  void staleExpectedValuesAreAConflictAndLeaveTheRowUnchanged() {
    // Otra persona ya le había asignado la novedad 3: la pantalla creía que no tenía.
    assertThatThrownBy(
            () ->
                service.assign(
                    territorial,
                    USER_TERRITORIAL_MULTI,
                    change(CASE_COMPLETE_WITH_NOVELTY, NOVELTY_OTHER_CAUSE, "Motivo")))
        .isInstanceOf(ConflictException.class);
    assertThat(stored(CASE_COMPLETE_WITH_NOVELTY))
        .containsEntry("IDFNOV", 3)
        .containsEntry("TEXT", null);

    // Mismo código, texto distinto al guardado.
    assertThatThrownBy(
            () ->
                service.remove(
                    territorial,
                    USER_TERRITORIAL_MULTI,
                    new NoveltyRemoveRequest(
                        year, CASE_NOVELTY_OTHER_CAUSE, NOVELTY_OTHER_CAUSE, "Texto viejo")))
        .isInstanceOf(ConflictException.class);
    assertThat(stored(CASE_NOVELTY_OTHER_CAUSE))
        .containsEntry("IDFNOV", 8)
        .containsEntry("TEXT", OTHER_CAUSE_TEXT);
  }

  @Test
  void personOfAnotherDepartmentOrMissingIsNotFoundWithoutChanges() {
    Throwable otherDepartment =
        catchThrowable(
            () ->
                service.assign(
                    territorial,
                    USER_TERRITORIAL_MULTI,
                    change(CASE_NO_CATALOG_WITH_KEY, NOVELTY_COMMON)));
    assertThat(otherDepartment).isInstanceOf(ResourceNotFoundException.class);
    assertThat(stored(CASE_NO_CATALOG_WITH_KEY)).containsEntry("IDFNOV", null);

    Throwable missing =
        catchThrowable(
            () ->
                service.remove(
                    territorial,
                    USER_TERRITORIAL_MULTI,
                    new NoveltyRemoveRequest(year, MISSING_CASE, null, null)));
    assertThat(missing).isInstanceOf(ResourceNotFoundException.class);
    // El mensaje no revela si la persona existe en otro departamento ni lleva la cédula.
    assertThat(otherDepartment.getMessage())
        .isEqualTo(missing.getMessage())
        .doesNotContain("9000000");
  }

  @Test
  void auditRecordsCodesWithoutPersonalData(CapturedOutput output) {
    service.assign(
        territorial,
        USER_TERRITORIAL_MULTI,
        new NoveltyChangeRequest(
            year, CASE_NOT_STARTED, NOVELTY_OTHER_CAUSE, TYPOGRAPHIC_TEXT, null, null));
    // IDMUES que no cumple el patrón de AdminAudit: no se registra.
    jdbc.update("UPDATE EDIN_MUESTRA SET IDMUES = 'IDM 26/04' WHERE C_C = ?", CASE_NOT_STARTED);
    service.remove(
        territorial,
        USER_TERRITORIAL_MULTI,
        new NoveltyRemoveRequest(year, CASE_NOT_STARTED, NOVELTY_OTHER_CAUSE, TYPOGRAPHIC_TEXT));
    jdbc.update("UPDATE EDIN_MUESTRA SET IDMUES = NULL WHERE C_C = ?", CASE_NOT_STARTED);
    service.assign(territorial, USER_TERRITORIAL_MULTI, change(CASE_NOT_STARTED, NOVELTY_COMMON));

    assertThat(output.getAll())
        .contains("usuario=" + USER_TERRITORIAL_MULTI)
        .contains("anio=" + year)
        .contains("operacion=novelty-assign codigos=[IDM" + year + "F04, ninguna, 8]")
        .contains("operacion=novelty-remove codigos=[sin-idmues, 8, ninguna]")
        .contains("operacion=novelty-assign codigos=[sin-idmues, ninguna, 3]")
        .doesNotContain(CASE_NOT_STARTED)
        .doesNotContain("Persona Ficticia 04")
        .doesNotContain("Cambio de sede")
        .doesNotContain("IDM 26/04");
  }

  @Test
  void listsCatalogEntitiesAndPeopleOfTheDepartment() {
    List<NoveltyCatalogEntry> catalog = service.catalog(territorial, year);
    assertThat(catalog)
        .contains(
            new NoveltyCatalogEntry(NOVELTY_COMMON, "Retiro ficticio", false),
            new NoveltyCatalogEntry(NOVELTY_OTHER_CAUSE, "Otra causa", true));
    assertThat(service.catalog(territorial, 1990)).isEmpty();

    List<NoveltyEntity> entities = service.entities(territorial, year, DEPARTMENT_ANTIOQUIA);
    // Activos del departamento 5: casos 1 a 7, 12 y 13; con novedad: 2, 5 y 6.
    assertThat(entities)
        .containsExactly(new NoveltyEntity(ENTITY_MIXED, "Entidad Ficticia Mixta", 9, 3));

    List<NoveltyPerson> people =
        service.people(territorial, year, DEPARTMENT_ANTIOQUIA, (long) ENTITY_MIXED);
    assertThat(people).hasSize(9);
    assertThat(people).extracting(NoveltyPerson::respondentCode).doesNotContain(CASE_INACTIVE);
    NoveltyPerson other = person(CASE_NOVELTY_OTHER_CAUSE);
    assertThat(other.noveltyCode()).isEqualTo(NOVELTY_OTHER_CAUSE);
    assertThat(other.otherCause()).isEqualTo(OTHER_CAUSE_TEXT);
    assertThat(other.status()).isEqualTo(SampleStatus.NOVELTY);
  }

  @Test
  void otherDepartmentAndAccessWithoutNoveltyManagementAreForbidden() {
    assertThatThrownBy(() -> service.entities(territorial, year, DEPARTMENT_VALLE))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> service.people(territorial, year, DEPARTMENT_VALLE, (long) ENTITY_NO_CATALOG))
        .isInstanceOf(AccessDeniedException.class);

    OperationalAccess operative =
        accessService.accessFor(
            SessionPrincipal.administrator(USER_OPERATIVE, EnumSet.of(Role.ADMIN_OPERATIVO), null));
    assertThatThrownBy(() -> service.catalog(operative, year))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                service.assign(operative, USER_OPERATIVE, change(CASE_NOT_STARTED, NOVELTY_COMMON)))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(stored(CASE_NOT_STARTED)).containsEntry("IDFNOV", null);
  }

  // ---- utilidades -------------------------------------------------------------------------

  private NoveltyChangeRequest change(String code, int noveltyCode) {
    return new NoveltyChangeRequest(year, code, noveltyCode, null, null, null);
  }

  private NoveltyChangeRequest change(String code, int noveltyCode, String text) {
    return new NoveltyChangeRequest(year, code, noveltyCode, text, null, null);
  }

  private NoveltyPerson person(String code) {
    return service.people(territorial, year, DEPARTMENT_ANTIOQUIA, (long) ENTITY_MIXED).stream()
        .filter(person -> code.equals(person.respondentCode()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No aparece el caso " + code));
  }

  /** IDFNOV (entero o nulo) y NOV_OTRA_CAUSA tal como quedaron en la base. */
  private Map<String, Object> stored(String code) {
    return jdbc.queryForObject(
        "SELECT IDFNOV, NOV_OTRA_CAUSA FROM EDIN_MUESTRA WHERE C_C = ? AND ANIO = ?",
        (row, index) -> {
          java.math.BigDecimal novelty = row.getBigDecimal("IDFNOV");
          Map<String, Object> values = new java.util.HashMap<>();
          values.put("IDFNOV", novelty == null ? null : novelty.intValueExact());
          values.put("TEXT", row.getString("NOV_OTRA_CAUSA"));
          return values;
        },
        code,
        String.valueOf(year));
  }
}
