package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.service.error.AnswerValidationException;
import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AnswerTableResolverIT extends OracleIntegrationTest {

  private static final String MAPPED = "EDIN_21_CAP_B_UBICA";
  private static final String WITHOUT_KEY = "EDIN_21_SIN_CC";

  @Autowired private NamedParameterJdbcTemplate jdbc;
  @Autowired private SurveyProperties survey;
  @Autowired private AnswerTableResolver activeResolver;

  private RulesFileRepository rulesFiles;
  private AnswerTableResolver resolver;

  @BeforeEach
  void setUp() {
    dropQuietly(MAPPED);
    dropQuietly(WITHOUT_KEY);
    jdbc.getJdbcTemplate()
        .execute("CREATE TABLE " + MAPPED + " AS SELECT * FROM EDIN_20_CAP_B_UBICA WHERE 1 = 0");
    jdbc.getJdbcTemplate().execute("CREATE TABLE " + WITHOUT_KEY + " (X VARCHAR2(1))");
    rulesFiles = mock(RulesFileRepository.class);
    resolver = new AnswerTableResolver(jdbc, survey, rulesFiles);
  }

  @AfterEach
  void tearDown() {
    dropQuietly(MAPPED);
    dropQuietly(WITHOUT_KEY);
  }

  private void dropQuietly(String table) {
    try {
      jdbc.getJdbcTemplate().execute("DROP TABLE " + table + " PURGE");
    } catch (RuntimeException ignored) {
      // No existía: nada que limpiar.
    }
  }

  private void mapTables(Map<String, String> tables) {
    when(rulesFiles.find("EDIN", 2027))
        .thenReturn(Optional.of(RuleSet.empty("EDIN", 2027).withTables(tables)));
  }

  @Test
  void defaultsToFixedTablesWhenYearHasNoMapping() {
    assertThat(resolver.tableFor(Chapter.B, "EDIN", 2027)).isEqualTo("EDIN_20_CAP_B_UBICA");
    mapTables(Map.of());
    assertThat(resolver.tableFor(Chapter.H, "EDIN", 2027)).isEqualTo("EDIN_20_CAP_H_PREV_PRACT");
    for (Chapter chapter : Chapter.values()) {
      assertThat(activeResolver.activeTableFor(chapter)).isEqualTo(chapter.tableName());
    }
  }

  @Test
  void usesMappedTable() {
    mapTables(Map.of("B", MAPPED));

    assertThat(resolver.tableFor(Chapter.B, "EDIN", 2027)).isEqualTo(MAPPED);
    assertThat(resolver.tableFor(Chapter.C, "EDIN", 2027)).isEqualTo(Chapter.C.tableName());
  }

  @Test
  void rejectsUnsafeOrForeignTables() {
    for (String unsafe :
        List.of("X; DROP", "DUAL", WITHOUT_KEY, "EDIN_99_NO_EXISTE", "edin_21_cap_b_ubica")) {
      mapTables(Map.of("B", unsafe));
      resolver.evict();
      assertThatThrownBy(() -> resolver.tableFor(Chapter.B, "EDIN", 2027))
          .isInstanceOfSatisfying(
              AnswerValidationException.class,
              exception -> {
                assertThat(exception.fieldMessages()).containsOnlyKeys("tablas.B");
                assertThat(exception.fieldMessages().get("tablas.B"))
                    .contains("no existe")
                    .doesNotContain(unsafe);
              });
    }
  }
}
