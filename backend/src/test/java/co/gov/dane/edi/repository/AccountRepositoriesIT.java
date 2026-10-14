package co.gov.dane.edi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.model.AdminAccount;
import co.gov.dane.edi.model.RespondentAccount;
import co.gov.dane.edi.support.OracleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountRepositoriesIT extends OracleIntegrationTest {

  @Autowired private RespondentAccountRepository respondents;
  @Autowired private AdminAccountRepository admins;

  @Test
  void findsActiveRespondentOfTheCurrentYear() {
    RespondentAccount account = respondents.findByLogin("PRUEBA01").orElseThrow();
    assertThat(account.enabledForWeb()).isTrue();
    assertThat(account.storedPassword()).isEqualTo("Clave#01");
    assertThat(account.respondentCode()).isEqualTo("1000000001");
  }

  @Test
  void marksInactiveRespondentAsNotEnabled() {
    assertThat(respondents.findByLogin("PRUEBA02").orElseThrow().enabledForWeb()).isFalse();
  }

  @Test
  void ignoresRespondentsOfOtherYearsAndUnknownLogins() {
    assertThat(respondents.findByLogin("PRUEBA03")).isEmpty();
    assertThat(respondents.findByLogin("NOEXISTE")).isEmpty();
  }

  @Test
  void respondentToStringDoesNotExposeSecrets() {
    String text = respondents.findByLogin("PRUEBA01").orElseThrow().toString();
    assertThat(text).doesNotContain("Clave#01").doesNotContain("1000000001");
  }

  @Test
  void findsAdministratorIgnoringUsernameCase() {
    AdminAccount account = admins.findByUsername("ADM.NACIONAL").orElseThrow();
    assertThat(account.legacyRole()).isEqualTo(1);
    assertThat(account.toString()).doesNotContain("Nac#2026x");
  }

  @Test
  void returnsDepartmentOfTerritorialAdministrator() {
    assertThat(admins.findByUsername("adm.bogota").orElseThrow().departmentCode()).isEqualTo("11");
  }

  @Test
  void ignoresAdministratorsOfAnotherSurvey() {
    assertThat(admins.findByUsername("adm.edid")).isEmpty();
  }
}
