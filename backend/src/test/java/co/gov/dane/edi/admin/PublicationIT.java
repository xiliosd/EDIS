package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.rules.Condition;
import co.gov.dane.edi.rules.ExpectedOutcome;
import co.gov.dane.edi.rules.QuestionFacts;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.SkipAction;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.rules.TestCase;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import co.gov.dane.edi.support.PublishedYearFixture;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

class PublicationIT extends OracleIntegrationTest {

  private static final String DRAFT = "/api/v1/admin/questionnaire/EDIN/2027/publication";
  private static final String ACTIVE = "/api/v1/admin/questionnaire/EDIN/2026/publication";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private SurveyProperties survey;
  @Autowired private YearCopyService yearCopy;
  @Autowired private QuestionnaireService questionnaire;
  @Autowired private AnswerColumnRepository answerColumns;
  @Autowired private QuestionFactsService factsService;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private PublicationService publication;

  private PublishedYearFixture fixture;

  @BeforeEach
  void installDraftYear() {
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
    fixture.installDraft();
  }

  @AfterEach
  void removeDraftYear() {
    fixture.remove();
  }

  @Test
  void checklistOfCleanDraftIsReady() throws Exception {
    fixture.createMissingColumns(2027);

    mockMvc
        .perform(get(DRAFT).cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.ready").value(true))
        .andExpect(jsonPath("$.items.length()").value(4))
        .andExpect(jsonPath("$.items[0].code").value("SIN_ERRORES"))
        .andExpect(jsonPath("$.items[0].passed").value(true))
        .andExpect(jsonPath("$.items[0].detail").value("El análisis no tiene errores."))
        .andExpect(jsonPath("$.items[1].code").value("CASOS_EN_VERDE"))
        .andExpect(jsonPath("$.items[1].passed").value(true))
        .andExpect(jsonPath("$.items[1].detail").value("El caso de prueba pasa."))
        .andExpect(jsonPath("$.items[2].code").value("COLUMNAS_CREADAS"))
        .andExpect(jsonPath("$.items[2].passed").value(true))
        .andExpect(jsonPath("$.items[3].code").value("TABLAS_VALIDAS"))
        .andExpect(jsonPath("$.items[3].passed").value(true))
        .andExpect(jsonPath("$.items[*].elements[*]").isEmpty());
  }

  @Test
  void checklistWithoutCasesPassesWithItsOwnDetail() throws Exception {
    fixture.createMissingColumns(2027);
    RuleSet draft = rulesFiles.load("EDIN", 2027);
    rulesFiles.save(
        draft.withCases(List.of()), draft.version(), "fixture", "borrar-caso", List.of());

    mockMvc
        .perform(get(DRAFT).cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(true))
        .andExpect(jsonPath("$.items[1].detail").value("No hay casos de prueba."));
  }

  @Test
  void publishBlockedByRedOrUnreviewedCaseOrPendingColumn() throws Exception {
    // 1. Columnas pendientes: las del dato de desarrollo (entre ellas B99) no existen en Oracle.
    List<String> pending = pendingColumns(2027);
    assertThat(pending).contains("B99");
    send(post(DRAFT), "{\"version\":1}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.detail")
                .value("El cuestionario no cumple las condiciones para publicarse."))
        .andExpect(jsonPath("$.checklist.ready").value(false))
        .andExpect(
            jsonPath("$.checklist.items[?(@.code == 'COLUMNAS_CREADAS')].passed").value(false))
        .andExpect(
            jsonPath("$.checklist.items[?(@.code == 'COLUMNAS_CREADAS')].detail")
                .value("Faltan " + pending.size() + " columnas; descargue el script."))
        .andExpect(
            jsonPath(
                "$.checklist.items[?(@.code == 'COLUMNAS_CREADAS')].elements[*]",
                contains(pending.toArray(new String[0]))));
    assertStillDraftAtVersion(1);

    // 2. Un caso que falla.
    fixture.createMissingColumns(2027);
    RuleSet draft = rulesFiles.load("EDIN", 2027);
    TestCase failing =
        new TestCase(
            "caso-falla",
            "Espera ocultar C01B con C01A en Sí",
            "C1",
            Map.of("C01A", "1"),
            new ExpectedOutcome(List.of("C01B"), null),
            null);
    List<TestCase> cases = new ArrayList<>(draft.cases());
    cases.add(failing);
    RuleSet withFailing =
        rulesFiles.save(draft.withCases(cases), 1, "fixture", "editar-caso", List.of());
    send(post(DRAFT), "{\"version\":2}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.checklist.items[?(@.code == 'CASOS_EN_VERDE')].passed").value(false))
        .andExpect(
            jsonPath("$.checklist.items[?(@.code == 'CASOS_EN_VERDE')].detail")
                .value("Hay casos que fallan o están por revisar."))
        .andExpect(
            jsonPath("$.checklist.items[?(@.code == 'CASOS_EN_VERDE')].elements[0]")
                .value("caso-falla"))
        .andExpect(
            jsonPath("$.checklist.items[?(@.code == 'COLUMNAS_CREADAS')].passed").value(true));
    assertStillDraftAtVersion(2);

    // 3. Un caso por revisar (sin el que falla).
    List<TestCase> unreviewed =
        draft.cases().stream().map(testCase -> testCase.withNeedsReview(true)).toList();
    rulesFiles.save(withFailing.withCases(unreviewed), 2, "fixture", "editar-caso", List.of());
    send(post(DRAFT), "{\"version\":3}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.checklist.ready").value(false))
        .andExpect(jsonPath("$.checklist.items[?(@.code == 'CASOS_EN_VERDE')].passed").value(false))
        .andExpect(
            jsonPath("$.checklist.items[?(@.code == 'CASOS_EN_VERDE')].elements[0]")
                .value("c01a-no-oculta-c01b"));
    assertStillDraftAtVersion(3);
  }

  @Test
  void publishBlockedByInvalidMappedTable() throws Exception {
    fixture.createMissingColumns(2027);
    RuleSet draft = rulesFiles.load("EDIN", 2027);
    rulesFiles.save(
        draft.withTables(Map.of("B", "TABLA_QUE_NO_EXISTE")),
        draft.version(),
        "fixture",
        "editar-tablas",
        List.of("B"));

    send(post(DRAFT), "{\"version\":2}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.checklist.items[?(@.code == 'TABLAS_VALIDAS')].passed").value(false))
        .andExpect(
            jsonPath("$.checklist.items[?(@.code == 'TABLAS_VALIDAS')].detail")
                .value("Hay tablas del mapeo que no existen o no son válidas."))
        .andExpect(
            jsonPath("$.checklist.items[?(@.code == 'TABLAS_VALIDAS')].elements[0]").value("B"));
    assertStillDraftAtVersion(2);
  }

  @Test
  void publishBlockedByAnalysisErrors() throws Exception {
    fixture.createMissingColumns(2027);
    RuleSet draft = rulesFiles.load("EDIN", 2027);
    SkipRule broken =
        new SkipRule(
            "apunta-a-z99",
            null,
            new Condition("Z99", "eq", "1", null, null, null, null, null),
            new SkipAction("deshabilitar", List.of("C01B")));
    List<SkipRule> skips = new ArrayList<>(draft.skips());
    skips.add(broken);
    rulesFiles.save(draft.withSkips(skips), 1, "fixture", "editar-salto", List.of());

    send(post(DRAFT), "{\"version\":2}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.checklist.items[?(@.code == 'SIN_ERRORES')].passed").value(false))
        .andExpect(
            jsonPath("$.checklist.items[?(@.code == 'SIN_ERRORES')].elements[0]")
                .value("regla:apunta-a-z99"))
        .andExpect(
            jsonPath("$.checklist.items[?(@.code == 'SIN_ERRORES')].detail")
                .value("El análisis tiene 1 error."));
    assertStillDraftAtVersion(2);
  }

  @Test
  void publishSwitchesStateRecordsWarningsAndLiftsSurveyGate() throws Exception {
    fixture.createMissingColumns(2027);
    fixture.createMissingColumns(2026);
    // El año activo (2026) en borrador: el encuestado recibe 503 hasta que se publique.
    Path activeFile =
        Path.of(survey.rulesDir()).resolve(survey.code() + "-" + survey.year() + ".json");
    byte[] original = Files.readAllBytes(activeFile);
    try {
      RuleSet active = rulesFiles.load(survey.code(), survey.year());
      SkipRule hidesEarlier =
          new SkipRule(
              "c01b-oculta-b06",
              null,
              new Condition("C01B", "eq", "1", null, null, null, null, null),
              new SkipAction("deshabilitar", List.of("B06")));
      List<SkipRule> skips = new ArrayList<>(active.skips());
      skips.add(hidesEarlier);
      jsonMapper.writeValue(
          activeFile.toFile(), active.withSkips(skips).withState(YearState.BORRADOR));
      rulesFiles.evict();
      questionnaire.evict();
      Cookie respondent = session(SessionPrincipal.respondent("1000000001"));
      mockMvc
          .perform(get("/api/v1/survey/chapters").cookie(respondent))
          .andExpect(status().isServiceUnavailable());
      int version = rulesFiles.load(survey.code(), survey.year()).version();

      ResultActions published = send(post(ACTIVE), "{\"version\":" + version + "}");

      published
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.version").value(version + 1))
          .andExpect(jsonPath("$.ready").value(true));
      RuleSet saved = rulesFiles.load(survey.code(), survey.year());
      assertThat(saved.effectiveState()).isEqualTo(YearState.PUBLICADO);
      assertThat(saved.version()).isEqualTo(version + 1);
      assertThat(saved.publication().user()).isEqualTo("admin:adm.cuest");
      assertThat(saved.publication().version()).isEqualTo(version + 1);
      assertThat(saved.publication().publishedAt()).isNotNull();
      assertThat(saved.publication().warnings())
          .isNotEmpty()
          .allMatch(warning -> warning.matches("[A-Z_]+@\\S+"))
          .anyMatch(warning -> warning.startsWith("DESTINO_ANTES@"));
      assertThat(saved.lastChange().operation()).isEqualTo("publicar");
      mockMvc.perform(get("/api/v1/survey/chapters").cookie(respondent)).andExpect(status().isOk());
      mockMvc
          .perform(get(ACTIVE).cookie(admin()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.version").value(version + 1))
          .andExpect(jsonPath("$.warnings[?(@ =~ /DESTINO_ANTES@.*/)]").isNotEmpty());
    } finally {
      Files.write(activeFile, original);
      rulesFiles.evict();
      questionnaire.evict();
    }
  }

  @Test
  void publishedYearCannotBePublishedAgain() throws Exception {
    int version = rulesFiles.load("EDIN", 2026).version();
    mockMvc.perform(get(ACTIVE).cookie(admin())).andExpect(status().isOk());

    send(post(ACTIVE), "{\"version\":" + version + "}")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("El cuestionario de 2026 está publicado y no se puede editar."));
    assertThat(rulesFiles.load("EDIN", 2026).version()).isEqualTo(version);
  }

  @Test
  void staleVersionGets409() throws Exception {
    fixture.createMissingColumns(2027);

    send(post(DRAFT), "{\"version\":7}")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("Otro usuario modificó el cuestionario. Recargue la página."));
    assertStillDraftAtVersion(1);
  }

  @Test
  void nonAdminGets403AndCsrfRequired() throws Exception {
    Cookie respondent = session(SessionPrincipal.respondent("1000000001"));
    mockMvc.perform(get(DRAFT)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(DRAFT).cookie(respondent)).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post(DRAFT)
                .with(realCsrf(mockMvc))
                .cookie(respondent)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isForbidden());
    // Sin el token CSRF la escritura se rechaza aunque el rol sea el correcto.
    mockMvc
        .perform(
            post(DRAFT)
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isForbidden());
    send(post(DRAFT), "{}").andExpect(status().isBadRequest());
    assertStillDraftAtVersion(1);
  }

  private void assertStillDraftAtVersion(int version) {
    RuleSet file = rulesFiles.load("EDIN", 2027);
    assertThat(file.effectiveState()).isEqualTo(YearState.BORRADOR);
    assertThat(file.version()).isEqualTo(version);
    assertThat(file.publication()).isNull();
  }

  private List<String> pendingColumns(int year) {
    return factsService.facts("EDIN", year).values().stream()
        .filter(question -> question.columnStatus() == ColumnStatus.PENDING_COLUMN)
        .map(QuestionFacts::column)
        .toList();
  }

  private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
    builder.with(realCsrf(mockMvc)).cookie(admin());
    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(builder);
  }

  private Cookie admin() {
    return session(
        SessionPrincipal.administrator("adm.cuest", EnumSet.of(Role.ADMIN_CUESTIONARIO), null));
  }

  private Cookie session(SessionPrincipal principal) {
    return new Cookie(SessionTokenService.SESSION_COOKIE_NAME, tokens.issueToken(principal));
  }
}
