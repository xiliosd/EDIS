package co.gov.dane.edi.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OperationalAccessTest {

  @Test
  void withoutDepartmentsAndNotNationalIsForbiddenEvenWithoutRequest() {
    OperationalAccess nobody = new OperationalAccess(false, List.of(), true, false);

    assertThatThrownBy(() -> nobody.departmentCodesOrAll(null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> nobody.departmentCodesOrAll(5))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void territorialGetsItsDepartmentsWhenNoneRequested() {
    OperationalAccess territorial =
        new OperationalAccess(
            false, List.of(new Department(5, "A"), new Department(8, "B")), true, false);

    assertThat(territorial.departmentCodesOrAll(null)).containsExactly(5, 8);
    assertThat(territorial.departmentCodesOrAll(8)).containsExactly(8);
  }

  @Test
  void nationalWithEmptyCatalogDoesNotFail() {
    OperationalAccess national = new OperationalAccess(true, List.of(), false, true);

    assertThat(national.departmentCodesOrAll(null)).isEmpty();
    assertThat(national.departmentCodesOrAll(5)).containsExactly(5);
  }
}
