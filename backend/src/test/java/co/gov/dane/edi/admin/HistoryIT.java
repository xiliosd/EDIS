package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.rules.Condition;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.SkipAction;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import co.gov.dane.edi.support.PublishedYearFixture;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Historial de versiones del año 2027 y restauración en borrador. */
@ExtendWith(OutputCaptureExtension.class)
class HistoryIT extends OracleIntegrationTest {

  private static final String HISTORY = "/api/v1/admin/questionnaire/EDIN/2027/history";
  private static final String RULES = "/api/v1/admin/questionnaire/EDIN/2027/rules";
  private static final String REASON = "Se corrige la redacción según el comité técnico";
  private static final String MISSING = "La versión no existe.";
  private static final String PUBLISHED_RESTORE =
      "Solo se puede restaurar una versión en borrador.";
  private static final String STALE = "Otro usuario modificó el cuestionario. Recargue la página.";

  private static final String HARMLESS_SKIP =
      """
      {"rule":{"id":"c01a-si-oculta-c01b","si":{"pregunta":"C01A","op":"eq","valor":"1"},
        "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},"version":%d}""";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private SurveyProperties survey;
  @Autowired private YearCopyService yearCopy;
  @Autowired private QuestionnaireService questionnaire;
  @Autowired private AnswerColumnRepository answerColumns;
  @Autowired private QuestionFactsService factsService;
  @Autowired private PublicationService publication;

  private PublishedYearFixture fixture;

  @BeforeEach
  void createFixture() {
    fixture =
        new PublishedYearFixture(
            jdbc,
            rulesFiles,
            survey,
            yearCopy,
            factsService,
            answerColumns,
            questionnaire,
            publication);
  }

  @AfterEach
  void removeYear() {
    fixture.remove();
  }

  @Test
  void historyListsPublishAndCorrectionsWithReasons() throws Exception {
    fixture.installPublished("fixture");
    long c01a = fixture.draft().draftCapProy(301);
    send(
            put("/api/v1/admin/questionnaire/EDIN/2027/entries/" + c01a + "/text"),
            "{\"text\":\"Texto corregido\",\"version\":2,\"motivo\":\"" + REASON + "\"}")
        .andExpect(status().isOk());

    send(get(HISTORY), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3))
        .andExpect(jsonPath("$.state").value("publicado"))
        .andExpect(jsonPath("$.entries.length()").value(3))
        .andExpect(jsonPath("$.entries[0].version").value(3))
        .andExpect(jsonPath("$.entries[0].current").value(true))
        .andExpect(jsonPath("$.entries[0].operation").value("corregir-editar-texto"))
        .andExpect(jsonPath("$.entries[0].reason").value(REASON))
        .andExpect(jsonPath("$.entries[0].user").value("admin:adm.cuest"))
        .andExpect(jsonPath("$.entries[1].version").value(2))
        .andExpect(jsonPath("$.entries[1].current").value(false))
        .andExpect(jsonPath("$.entries[1].operation").value("publicar"))
        .andExpect(jsonPath("$.entries[2].version").value(1))
        .andExpect(jsonPath("$.entries[2].current").value(false));
  }

  @Test
  void readingAnOldVersion() throws Exception {
    fixture.installDraft();
    send(put(RULES + "/skips/c01a-si-oculta-c01b"), HARMLESS_SKIP.formatted(1))
        .andExpect(status().isOk());

    send(get(HISTORY + "/1"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.estado").value("borrador"))
        .andExpect(jsonPath("$.saltos[?(@.id == 'c01a-si-oculta-c01b')]").isEmpty());
    send(get(HISTORY + "/2"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.saltos[?(@.id == 'c01a-si-oculta-c01b')]").isNotEmpty());
  }

  @Test
  void restoreReplacesRulesButNotStateOrFrm() throws Exception {
    fixture.installDraft();
    RuleSet original = rulesFiles.load("EDIN", 2027);
    send(put(RULES + "/skips/c01a-si-oculta-c01b"), HARMLESS_SKIP.formatted(1))
        .andExpect(status().isOk());
    List<Long> counts = frmCounts();

    send(post(HISTORY + "/1/restore"), "{\"version\":2}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3));

    RuleSet restored = rulesFiles.load("EDIN", 2027);
    assertThat(restored.version()).isEqualTo(3);
    assertThat(restored.effectiveState()).isEqualTo(YearState.BORRADOR);
    assertThat(restored.publication()).isNull();
    assertThat(restored.skips()).isEqualTo(original.skips());
    assertThat(restored.validations()).isEqualTo(original.validations());
    assertThat(restored.cases()).isEqualTo(original.cases());
    assertThat(restored.tables()).isEqualTo(original.tables());
    assertThat(restored.newColumns()).isEqualTo(original.newColumns());
    assertThat(restored.lastChange().operation()).isEqualTo("restaurar-version");
    assertThat(restored.lastChange().reason()).isNull();
    assertThat(frmCounts()).isEqualTo(counts);
  }

  @Test
  void restoreIsAuditedWithTheVersionCode(CapturedOutput output) throws Exception {
    fixture.installDraft();
    send(post(HISTORY + "/1/restore"), "{\"version\":1}").andExpect(status().isOk());

    assertThat(output.getAll()).contains("operacion=restaurar-version").contains("codigos=[v1]");
  }

  @Test
  void restoringTheCurrentVersionLeavesANewVersionWithTheSameContent() throws Exception {
    fixture.installDraft();
    RuleSet before = rulesFiles.load("EDIN", 2027);

    send(post(HISTORY + "/1/restore"), "{\"version\":1}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));

    RuleSet after = rulesFiles.load("EDIN", 2027);
    assertThat(after.version()).isEqualTo(2);
    assertThat(after.withVersion(1).withLastChange(null)).isEqualTo(before.withLastChange(null));
    assertThat(after.lastChange().operation()).isEqualTo("restaurar-version");
  }

  @Test
  void restoreOnPublishedYearIs409() throws Exception {
    fixture.installPublished("fixture");
    RuleSet published = rulesFiles.load("EDIN", 2027);
    List<Long> counts = frmCounts();

    send(post(HISTORY + "/1/restore"), "{\"version\":2}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(PUBLISHED_RESTORE));

    assertThat(rulesFiles.load("EDIN", 2027)).isEqualTo(published);
    assertThat(frmCounts()).isEqualTo(counts);
  }

  @Test
  void restoreThatAddsErrorsIs422() throws Exception {
    fixture.installDraft();
    RuleSet draft = rulesFiles.load("EDIN", 2027);
    SkipRule toUnknown =
        new SkipRule(
            "apunta-a-z99",
            null,
            new Condition("Z99", "eq", "1", null, null, null, null, null),
            new SkipAction("deshabilitar", List.of("C01B")));
    rulesFiles.save(
        draft.withSkips(List.of(toUnknown)), 1, "fixture", "editar-salto", List.of("apunta-a-z99"));
    RuleSet withError = rulesFiles.load("EDIN", 2027);
    rulesFiles.save(withError.withSkips(List.of()), 2, "fixture", "borrar-salto", List.of());

    send(post(HISTORY + "/2/restore"), "{\"version\":3}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.report.errors").isNotEmpty());

    RuleSet file = rulesFiles.load("EDIN", 2027);
    assertThat(file.version()).isEqualTo(3);
    assertThat(file.skips()).isEmpty();
  }

  @Test
  void restoreWithStaleVersionIs409() throws Exception {
    fixture.installDraft();

    send(post(HISTORY + "/1/restore"), "{\"version\":7}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(STALE));
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void restoreWithoutVersionIs400() throws Exception {
    fixture.installDraft();

    send(post(HISTORY + "/1/restore"), "{}").andExpect(status().isBadRequest());
  }

  @Test
  void missingVersionIs404() throws Exception {
    fixture.installDraft();

    send(get(HISTORY + "/99"), null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(MISSING));
    send(post(HISTORY + "/99/restore"), "{\"version\":1}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(MISSING));
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void nonIntegerVersionIs400() throws Exception {
    fixture.installDraft();

    send(get(HISTORY + "/abc"), null).andExpect(status().isBadRequest());
    send(post(HISTORY + "/abc/restore"), "{\"version\":1}").andExpect(status().isBadRequest());
  }

  @Test
  void unknownYearIs404() throws Exception {
    send(get("/api/v1/admin/questionnaire/EDIN/2099/history"), null)
        .andExpect(status().isNotFound());
  }

  private List<Long> frmCounts() {
    List<Long> counts = new ArrayList<>();
    for (FrmTable table : FrmTable.values()) {
      counts.add(jdbc.queryForObject("SELECT COUNT(*) FROM " + table.tableName(), Long.class));
    }
    return counts;
  }

  private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
    builder.with(realCsrf(mockMvc)).cookie(admin());
    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(builder);
  }

  private Cookie admin() {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(
            SessionPrincipal.administrator(
                "adm.cuest", EnumSet.of(Role.ADMIN_CUESTIONARIO), null)));
  }
}
