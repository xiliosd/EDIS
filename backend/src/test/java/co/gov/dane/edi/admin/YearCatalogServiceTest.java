package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YearCatalogServiceTest {

  private RulesFileRepository files;
  private YearCatalogService catalog;

  @BeforeEach
  void setUp() {
    files = mock(RulesFileRepository.class);
    when(files.years("EDIN")).thenReturn(List.of(2026, 2027));
    when(files.load("EDIN", 2026)).thenReturn(RuleSet.empty("EDIN", 2026));
    RuleSet draft = RuleSet.empty("EDIN", 2027).withState(YearState.BORRADOR).withVersion(3);
    when(files.load("EDIN", 2027)).thenReturn(draft);
    when(files.find("EDIN", 2027)).thenReturn(Optional.of(draft));
    when(files.find("EDIN", 2028)).thenReturn(Optional.empty());
    catalog = new YearCatalogService(files, new SurveyProperties("EDIN", 2026, "S", "d"));
  }

  @Test
  void listsYearsWithStateVersionAndActiveFlag() {
    assertThat(catalog.years("EDIN"))
        .extracting("year", "state", "version", "active")
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(2026, YearState.PUBLICADO, 0, true),
            org.assertj.core.groups.Tuple.tuple(2027, YearState.BORRADOR, 3, false));
  }

  @Test
  void findsTheDraftYear() {
    assertThat(catalog.draftYear("EDIN")).contains(2027);
  }

  @Test
  void requireDraftAcceptsDraftAndRejectsPublished() {
    catalog.requireDraft("EDIN", 2027);
    assertThatThrownBy(() -> catalog.requireDraft("EDIN", 2026))
        .isInstanceOf(ConflictException.class)
        .hasMessage("El cuestionario de 2026 está publicado y no se puede editar.");
  }

  @Test
  void unknownSurveyOrYearIsNotFound() {
    assertThatThrownBy(() -> catalog.years("XXXX")).isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> catalog.requireYear("EDIN", 2028))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
