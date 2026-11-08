package co.gov.dane.edi.operation;

import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_ANTIOQUIA;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_ATLANTICO;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_VALLE;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_ENGINEER;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_OPERATIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_TERRITORIAL_MULTI;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_TERRITORIAL_SINGLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.support.OperationalSampleFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/** Alcance operativo por rol y departamento sobre la muestra ficticia. */
class OperationalAccessServiceIT extends OracleIntegrationTest {

  @Autowired private OperationalAccessService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SurveyProperties survey;

  private OperationalSampleFixture fixture;

  @BeforeEach
  void loadFixture() {
    fixture = new OperationalSampleFixture(jdbc);
    fixture.load(survey.year());
    service.clearCache();
  }

  @AfterEach
  void clearFixture() {
    fixture.clear(survey.year());
    service.clearCache();
  }

  @Test
  void territorialWithSeveralAssignmentsSeesOnlyThose() {
    OperationalAccess access = service.accessFor(territorial(USER_TERRITORIAL_MULTI, "5"));

    assertThat(access.allDepartments()).isFalse();
    assertThat(codes(access)).containsExactlyInAnyOrder(DEPARTMENT_ANTIOQUIA, DEPARTMENT_ATLANTICO);
    assertThat(access.departments()).allSatisfy(d -> assertThat(d.name()).isNotBlank());
    assertThat(access.canManageNovelties()).isTrue();
    assertThat(access.canManageKeys()).isFalse();
  }

  @Test
  void territorialWithoutAssignmentsFallsBackToHomeDepartment() {
    OperationalAccess access = service.accessFor(territorial(USER_TERRITORIAL_SINGLE, "76"));

    assertThat(codes(access)).containsExactly(DEPARTMENT_VALLE);
  }

  @Test
  void operativeAndNationalSeeEveryDepartment() {
    OperationalAccess operative =
        service.accessFor(administrator(USER_OPERATIVE, Role.ADMIN_OPERATIVO));
    OperationalAccess national =
        service.accessFor(administrator(USER_ENGINEER, Role.ADMIN_NACIONAL));

    for (OperationalAccess access : List.of(operative, national)) {
      assertThat(access.allDepartments()).isTrue();
      assertThat(codes(access))
          .contains(DEPARTMENT_ANTIOQUIA, DEPARTMENT_ATLANTICO, DEPARTMENT_VALLE);
      assertThat(access.canManageNovelties()).isFalse();
    }
    assertThat(operative.canManageKeys()).isFalse();
    assertThat(national.canManageKeys()).isTrue();
  }

  @Test
  void questionnaireAdministratorHasNoOperationalScope() {
    OperationalAccess access =
        service.accessFor(administrator("adm.cuest", Role.ADMIN_CUESTIONARIO));

    assertThat(access.departments()).isEmpty();
    assertThat(access.canManageNovelties()).isFalse();
    assertThat(access.canManageKeys()).isFalse();
  }

  @Test
  void foreignDepartmentIsDeniedAndOwnIsAllowed() {
    OperationalAccess access = service.accessFor(territorial(USER_TERRITORIAL_MULTI, "5"));

    assertThatThrownBy(() -> access.requireDepartment(DEPARTMENT_VALLE))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> access.departmentCodesOrAll(DEPARTMENT_VALLE))
        .isInstanceOf(AccessDeniedException.class);
    assertThatCode(() -> access.requireDepartment(DEPARTMENT_ANTIOQUIA)).doesNotThrowAnyException();
    assertThat(access.departmentCodesOrAll(DEPARTMENT_ATLANTICO))
        .containsExactly(DEPARTMENT_ATLANTICO);
  }

  @Test
  void noRequestedDepartmentMeansTheUserOwnOrAll() {
    OperationalAccess territorial = service.accessFor(territorial(USER_TERRITORIAL_MULTI, "5"));
    OperationalAccess national =
        service.accessFor(administrator(USER_ENGINEER, Role.ADMIN_NACIONAL));

    assertThat(territorial.departmentCodesOrAll(null))
        .containsExactlyInAnyOrder(DEPARTMENT_ANTIOQUIA, DEPARTMENT_ATLANTICO);
    assertThat(national.departmentCodesOrAll(null))
        .contains(DEPARTMENT_ANTIOQUIA, DEPARTMENT_ATLANTICO, DEPARTMENT_VALLE);
    assertThatCode(() -> national.requireDepartment(DEPARTMENT_VALLE)).doesNotThrowAnyException();
  }

  @Test
  void assignmentForAnotherSurveyDoesNotCount() {
    jdbc.update(
        "UPDATE GRAL_TERRITORIAL_ACCESO SET PROY = 'EDID' WHERE COD_USUA = ? AND COD_DEPTO = ?",
        USER_TERRITORIAL_MULTI,
        DEPARTMENT_ATLANTICO);
    service.clearCache();

    OperationalAccess access = service.accessFor(territorial(USER_TERRITORIAL_MULTI, "5"));

    assertThat(codes(access)).containsExactly(DEPARTMENT_ANTIOQUIA);
  }

  private static SessionPrincipal territorial(String username, String departmentCode) {
    return SessionPrincipal.administrator(
        username, EnumSet.of(Role.ADMIN_TERRITORIAL), departmentCode);
  }

  private static SessionPrincipal administrator(String username, Role role) {
    return SessionPrincipal.administrator(username, EnumSet.of(role), null);
  }

  private static List<Integer> codes(OperationalAccess access) {
    return access.departments().stream().map(Department::code).toList();
  }
}
