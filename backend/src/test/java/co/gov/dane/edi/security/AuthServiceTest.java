package co.gov.dane.edi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.gov.dane.edi.model.AccountType;
import co.gov.dane.edi.model.AdminAccount;
import co.gov.dane.edi.model.LoginRequest;
import co.gov.dane.edi.model.RespondentAccount;
import co.gov.dane.edi.repository.AdminAccountRepository;
import co.gov.dane.edi.repository.RespondentAccountRepository;
import co.gov.dane.edi.service.error.InvalidCredentialsException;
import co.gov.dane.edi.service.error.TooManyLoginAttemptsException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

  private static final String ADDRESS = "10.0.0.1";

  private RespondentAccountRepository respondents;
  private AdminAccountRepository admins;
  private LoginAttemptService attempts;
  private AuthService service;

  @BeforeEach
  void setUp() {
    respondents = mock(RespondentAccountRepository.class);
    admins = mock(AdminAccountRepository.class);
    attempts = mock(LoginAttemptService.class);
    service = new AuthService(respondents, admins, attempts);
  }

  @Test
  void authenticatesEnabledRespondent() {
    when(respondents.findByLogin("PRUEBA01"))
        .thenReturn(Optional.of(new RespondentAccount("1000000001", "899999", "Clave#01", true)));

    SessionPrincipal principal =
        service.authenticate(request(AccountType.RESPONDENT, "PRUEBA01", "Clave#01"), ADDRESS);

    assertThat(principal.roles()).containsExactly(Role.ENCUESTADO);
    verify(attempts).recordSuccess(LoginAttemptService.keyFor("PRUEBA01", ADDRESS));
  }

  @Test
  void rejectsWrongPasswordAndRecordsFailure() {
    when(respondents.findByLogin("PRUEBA01"))
        .thenReturn(Optional.of(new RespondentAccount("1000000001", "899999", "Clave#01", true)));

    assertThatThrownBy(
            () ->
                service.authenticate(request(AccountType.RESPONDENT, "PRUEBA01", "otra"), ADDRESS))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(attempts).recordFailure(LoginAttemptService.keyFor("PRUEBA01", ADDRESS));
  }

  @Test
  void rejectsDisabledRespondentEvenWithCorrectPassword() {
    when(respondents.findByLogin("PRUEBA02"))
        .thenReturn(Optional.of(new RespondentAccount("1000000002", "899999", "Clave#02", false)));

    assertThatThrownBy(
            () ->
                service.authenticate(
                    request(AccountType.RESPONDENT, "PRUEBA02", "Clave#02"), ADDRESS))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void unknownUserGetsTheSameErrorAsWrongPassword() {
    when(respondents.findByLogin(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.authenticate(request(AccountType.RESPONDENT, "NADIE", "x"), ADDRESS))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Usuario o clave incorrectos.");
  }

  @Test
  void lockedKeyIsRejectedBeforeQueryingTheDatabase() {
    doThrow(new TooManyLoginAttemptsException(Duration.ofMinutes(10)))
        .when(attempts)
        .ensureNotLocked(anyString());

    assertThatThrownBy(
            () ->
                service.authenticate(
                    request(AccountType.RESPONDENT, "PRUEBA01", "Clave#01"), ADDRESS))
        .isInstanceOf(TooManyLoginAttemptsException.class);
    verifyNoInteractions(respondents, admins);
  }

  @Test
  void authenticatesNationalAdministratorWithQuestionnaireRole() {
    when(admins.findByUsername("adm.nacional"))
        .thenReturn(Optional.of(new AdminAccount("adm.nacional", "Nac#2026x", 1, null)));

    SessionPrincipal principal =
        service.authenticate(
            request(AccountType.ADMINISTRATOR, "adm.nacional", "Nac#2026x"), ADDRESS);

    assertThat(principal.roles())
        .containsExactlyInAnyOrder(Role.ADMIN_NACIONAL, Role.ADMIN_CUESTIONARIO);
    assertThat(principal.subject()).isEqualTo("admin:adm.nacional");
  }

  @Test
  void rejectsAdministratorWithUnknownLegacyRole() {
    when(admins.findByUsername("raro"))
        .thenReturn(Optional.of(new AdminAccount("raro", "Clave123", 9, null)));

    assertThatThrownBy(
            () ->
                service.authenticate(
                    request(AccountType.ADMINISTRATOR, "raro", "Clave123"), ADDRESS))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(attempts, never()).recordSuccess(anyString());
  }

  @Test
  void mapsEveryLegacyAdministratorRole() {
    assertThat(AuthService.rolesForLegacyAdminRole(1))
        .isEqualTo(EnumSet.of(Role.ADMIN_NACIONAL, Role.ADMIN_CUESTIONARIO));
    assertThat(AuthService.rolesForLegacyAdminRole(2)).containsExactly(Role.ADMIN_TERRITORIAL);
    assertThat(AuthService.rolesForLegacyAdminRole(3)).containsExactly(Role.ADMIN_OPERATIVO);
    assertThat(AuthService.rolesForLegacyAdminRole(4)).containsExactly(Role.ADMIN_OPERATIVO);
    assertThat(AuthService.rolesForLegacyAdminRole(0)).isEmpty();
  }

  @Test
  void passwordComparisonHandlesMissingStoredPassword() {
    assertThat(AuthService.passwordMatches(null, "")).isFalse();
    assertThat(AuthService.passwordMatches("abc", "abd")).isFalse();
    assertThat(AuthService.passwordMatches("abc", "abc")).isTrue();
  }

  private static LoginRequest request(AccountType type, String username, String password) {
    return new LoginRequest(type, username, password);
  }
}
