package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.rules.Condition;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RuleSetValidator;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.SkipAction;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.support.DraftYearFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RulesAdminIT extends OracleIntegrationTest {

  private static final String DRAFT = "/api/v1/admin/questionnaire/EDIN/2027/rules";
  private static final String PUBLISHED = "/api/v1/admin/questionnaire/EDIN/2026/rules";

  private static final String SIMULATOR_DRAFT = "/api/v1/admin/questionnaire/EDIN/2027/simulator";
  private static final String SIMULATOR_PUBLISHED =
      "/api/v1/admin/questionnaire/EDIN/2026/simulator";

  private static final String SKIP_C01A_YES =
      """
      {"rule":{"id":"c01a-si-oculta-c01b","descripcion":"C01A en Sí oculta C01B",
        "si":{"pregunta":"C01A","op":"eq","valor":"1"},
        "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},"version":%d}""";

  private static final String SKIP_WITH_UNKNOWN_OPTION =
      """
      {"rule":{"id":"c01a-tres","si":{"pregunta":"C01A","op":"eq","valor":"3"},
        "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},"version":1}""";

  private static final String CASE_BODY =
      """
      {"testCase":{"id":"c01a-si-caso","nombre":"C01A en Sí no oculta C01B","pagina":"C1",
        "respuestas":{"C01A":"1"},"esperado":{"ocultas":[]}},"version":%d}""";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private SurveyProperties survey;
  @Autowired private YearCopyService yearCopy;
  @Autowired private QuestionnaireService questionnaire;

  private DraftYearFixture fixture;

  @BeforeEach
  void installDraftYear() {
    fixture = new DraftYearFixture(jdbc, rulesFiles, survey, yearCopy);
    fixture.install();
    questionnaire.evict();
  }

  @AfterEach
  void removeDraftYear() {
    fixture.remove();
    questionnaire.evict();
  }

  @Test
  void viewListsRulesReportCasesAndQuestions() throws Exception {
    String c01a = "$.questions[?(@.column == 'C01A')]";
    mockMvc
        .perform(get(DRAFT).cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.survey").value("EDIN"))
        .andExpect(jsonPath("$.year").value(2027))
        .andExpect(jsonPath("$.state").value("borrador"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.skips[?(@.id == 'legacy-C01A')]").exists())
        .andExpect(jsonPath("$.legacySkipIds.length()").value(0))
        .andExpect(jsonPath("$.validations.B06.tipo").value("entero"))
        .andExpect(jsonPath("$.cases.length()").value(1))
        .andExpect(jsonPath("$.caseResults[0].id").value("c01a-no-oculta-c01b"))
        .andExpect(jsonPath("$.caseResults[0].status").value("pasa"))
        .andExpect(jsonPath("$.report.errors.length()").value(0))
        .andExpect(jsonPath(c01a + ".inputType").value("SINGLE_CHOICE"))
        .andExpect(jsonPath(c01a + ".pageCode").value("C1"))
        .andExpect(jsonPath(c01a + ".text").value("¿Pregunta ficticia C01A?"))
        .andExpect(jsonPath(c01a + ".options[0].value").value("1"))
        .andExpect(
            jsonPath("$.questions[?(@.column == 'B99')].columnStatus").value("PENDING_COLUMN"));
  }

  @Test
  void simulatorViewHasPagesAndEffectiveRulesWithoutAnswers() throws Exception {
    long before = answerRows();

    mockMvc
        .perform(get(SIMULATOR_DRAFT).cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.rules.encuesta").value("EDIN"))
        .andExpect(jsonPath("$.rules.anio").value(2027))
        .andExpect(jsonPath("$.rules.saltos[?(@.id == 'legacy-C01A')]").exists())
        .andExpect(jsonPath("$.rules.casos").doesNotExist())
        .andExpect(jsonPath("$.pages[0].pageCode").value("B1"))
        .andExpect(jsonPath("$.pages[?(@.pageCode == 'C1')].chapterCode").value("C"))
        .andExpect(jsonPath("$.pages[?(@.pageCode == 'C1')].chapterTitle").exists())
        .andExpect(jsonPath("$.pages[?(@.pageCode == 'C1')].module").exists())
        .andExpect(jsonPath("$.pages[0].state").doesNotExist())
        .andExpect(jsonPath("$.pages[0].answers").isEmpty())
        .andExpect(
            jsonPath("$.pages[?(@.pageCode == 'C1')].entries[0].answer.columnCode").value("C01A"));

    mockMvc.perform(get(SIMULATOR_PUBLISHED).cookie(admin())).andExpect(status().isOk());
    mockMvc.perform(get(SIMULATOR_DRAFT)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(SIMULATOR_DRAFT).cookie(session(SessionPrincipal.respondent("1000000001"))))
        .andExpect(status().isForbidden());
    assertThat(answerRows()).isEqualTo(before);
  }

  @Test
  void seededDevCasePasses() throws Exception {
    mockMvc
        .perform(get(PUBLISHED).cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("publicado"))
        .andExpect(jsonPath("$.legacySkipIds[0]").value("legacy-C01A"))
        .andExpect(
            jsonPath("$.caseResults[?(@.id == 'c01a-no-oculta-c01b')].status").value("pasa"));
  }

  @Test
  void saveSkipReturnsNewVersionReportAndCases() throws Exception {
    send(put(DRAFT + "/skips/c01a-si-oculta-c01b"), SKIP_C01A_YES.formatted(1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.report.errors.length()").value(0))
        .andExpect(jsonPath("$.caseResults[0].status").value("pasa"));

    RuleSet saved = rulesFiles.load("EDIN", 2027);
    assertThat(saved.version()).isEqualTo(2);
    assertThat(saved.skips())
        .extracting(SkipRule::id)
        .containsExactly("legacy-C01A", "c01a-si-oculta-c01b");
    send(put(DRAFT + "/skips/c01a-si-oculta-c01b"), SKIP_C01A_YES.formatted(2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3));
    assertThat(rulesFiles.load("EDIN", 2027).skips()).hasSize(2);
  }

  @Test
  void ruleAddingAnErrorIsRejectedAndFileUnchanged() throws Exception {
    send(put(DRAFT + "/skips/c01a-tres"), SKIP_WITH_UNKNOWN_OPTION)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.title").value("Las reglas tienen errores."))
        .andExpect(jsonPath("$.report.errors[0].code").value("VALOR_NO_ES_OPCION"))
        .andExpect(jsonPath("$.report.errors[0].element").value("regla:c01a-tres"));

    RuleSet unchanged = rulesFiles.load("EDIN", 2027);
    assertThat(unchanged.version()).isEqualTo(1);
    assertThat(unchanged.skips()).extracting(SkipRule::id).containsExactly("legacy-C01A");
  }

  @Test
  void preexistingErrorsDoNotBlockUnrelatedSave() throws Exception {
    RuleSet draft = rulesFiles.load("EDIN", 2027);
    SkipRule broken =
        new SkipRule(
            "apunta-a-z99",
            null,
            new Condition("Z99", "eq", "1", null, null, null, null, null),
            new SkipAction("deshabilitar", List.of("C01B")));
    rulesFiles.save(
        draft.withSkips(List.of(broken)), draft.version(), "fixture", "editar-salto", List.of());

    send(put(DRAFT + "/skips/c01a-si-oculta-c01b"), SKIP_C01A_YES.formatted(2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3))
        .andExpect(jsonPath("$.report.errors[0].element").value("regla:apunta-a-z99"));
  }

  @Test
  void importWithIndirectCycleIsRejectedAndFileUnchanged() throws Exception {
    String body =
        """
        {"rules":{"encuesta":"EDIN","anio":2027,"version":0,"saltos":[
          {"id":"a-oculta-b","si":{"pregunta":"C01A","op":"eq","valor":"1"},
           "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},
          {"id":"b-oculta-a","si":{"pregunta":"C01B","op":"eq","valor":"1"},
           "entonces":{"accion":"deshabilitar","preguntas":["C01A"]}}],
         "validaciones":{}},"version":1}""";

    send(put(DRAFT + "/import"), body)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.report.errors[?(@.code == 'CICLO')]").exists());

    RuleSet unchanged = rulesFiles.load("EDIN", 2027);
    assertThat(unchanged.version()).isEqualTo(1);
    assertThat(unchanged.skips()).extracting(SkipRule::id).containsExactly("legacy-C01A");
  }

  @Test
  void importWithMoreThanAThousandRulesIsRejectedAndFileUnchanged() throws Exception {
    StringBuilder skips = new StringBuilder();
    for (int index = 0; index <= RuleSetValidator.MAX_SKIPS; index++) {
      skips
          .append(index == 0 ? "" : ",")
          .append("{\"id\":\"r-")
          .append(index)
          .append("\",\"si\":{\"pregunta\":\"C01A\",\"op\":\"eq\",\"valor\":\"1\"},")
          .append("\"entonces\":{\"accion\":\"deshabilitar\",\"preguntas\":[\"C01B\"]}}");
    }
    String body =
        "{\"rules\":{\"encuesta\":\"EDIN\",\"anio\":2027,\"version\":0,\"saltos\":["
            + skips
            + "],\"validaciones\":{}},\"version\":1}";

    send(put(DRAFT + "/import"), body)
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.report.errors[?(@.code == 'ESTRUCTURA')].message")
                .value("El archivo tiene más de 1000 reglas."));

    RuleSet unchanged = rulesFiles.load("EDIN", 2027);
    assertThat(unchanged.version()).isEqualTo(1);
    assertThat(unchanged.skips()).extracting(SkipRule::id).containsExactly("legacy-C01A");
  }

  @Test
  void importKeepsStateTablesAndNewColumns() throws Exception {
    RuleSet before = rulesFiles.load("EDIN", 2027);
    String body =
        """
        {"rules":{"encuesta":"EDIN","anio":2027,"version":99,"estado":"publicado",
          "tablas":{"B":"OTRA_TABLA"},
          "columnasNuevas":{"X99":{"tipo":"NUMBER","longitud":3}},
          "saltos":[{"id":"c01a-si-oculta-c01b",
            "si":{"pregunta":"C01A","op":"eq","valor":"1"},
            "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}}],
          "validaciones":{},"casos":[]},"version":1}""";

    send(put(DRAFT + "/import"), body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.caseResults.length()").value(0));

    RuleSet after = rulesFiles.load("EDIN", 2027);
    assertThat(after.version()).isEqualTo(2);
    assertThat(after.state()).isEqualTo(before.state());
    assertThat(after.tables()).isEqualTo(before.tables());
    assertThat(after.newColumns()).isEqualTo(before.newColumns());
    assertThat(after.skips()).extracting(SkipRule::id).containsExactly("c01a-si-oculta-c01b");
    assertThat(after.validations()).isEmpty();
    assertThat(after.cases()).isEmpty();
  }

  @Test
  void importForAnotherSurveyOrYearIs422() throws Exception {
    send(
            put(DRAFT + "/import"),
            "{\"rules\":{\"encuesta\":\"EDIN\",\"anio\":2026,\"version\":0},\"version\":1}")
        .andExpect(status().isUnprocessableContent());
    send(
            put(DRAFT + "/import"),
            "{\"rules\":{\"encuesta\":\"OTRA\",\"anio\":2027,\"version\":0},\"version\":1}")
        .andExpect(status().isUnprocessableContent());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void exportDownloadsStoredFile() throws Exception {
    mockMvc
        .perform(get(DRAFT + "/export").cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"EDIN-2027.json\""))
        .andExpect(jsonPath("$.encuesta").value("EDIN"))
        .andExpect(jsonPath("$.anio").value(2027))
        .andExpect(jsonPath("$.estado").value("borrador"))
        .andExpect(jsonPath("$.casos[0].id").value("c01a-no-oculta-c01b"));
  }

  @Test
  void validationOnOptionQuestionReportsAnErrorAndIsRejected() throws Exception {
    send(
            put(DRAFT + "/validations/C01B"),
            "{\"validation\":{\"obligatoria\":false,\"tipo\":\"entero\",\"min\":1},\"version\":1}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.report.errors[0].code").value("VALIDACION_EN_OPCION"));
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void validationsAreUpsertedAndDeleted() throws Exception {
    send(
            put(DRAFT + "/validations/B06"),
            "{\"validation\":{\"obligatoria\":true,\"tipo\":\"entero\",\"min\":18,\"max\":90},"
                + "\"version\":1}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    assertThat(rulesFiles.load("EDIN", 2027).validations().get("B06").min().intValue())
        .isEqualTo(18);

    send(delete(DRAFT + "/validations/B06?version=2"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3));
    assertThat(rulesFiles.load("EDIN", 2027).validations()).isEmpty();
    send(delete(DRAFT + "/validations/B06?version=3"), null).andExpect(status().isNotFound());
  }

  @Test
  void invalidValidationColumnIs422() throws Exception {
    send(put(DRAFT + "/validations/c01b"), "{\"validation\":{\"obligatoria\":true},\"version\":1}")
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void skipsCanBeDeletedAndNewIdsMustMatchThePattern() throws Exception {
    send(put(DRAFT + "/skips/c01a-si-oculta-c01b"), SKIP_C01A_YES.formatted(1))
        .andExpect(status().isOk());
    send(delete(DRAFT + "/skips/c01a-si-oculta-c01b?version=2"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3));
    assertThat(rulesFiles.load("EDIN", 2027).skips())
        .extracting(SkipRule::id)
        .containsExactly("legacy-C01A");

    send(
            put(DRAFT + "/skips/Mala_Regla"),
            """
            {"rule":{"id":"Mala_Regla","si":{"pregunta":"C01A","op":"eq","valor":"1"},
              "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},"version":3}""")
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void casesAreUpsertedAndSavingClearsReviewMark() throws Exception {
    RuleSet draft = rulesFiles.load("EDIN", 2027);
    rulesFiles.save(
        draft.withCases(List.of(draft.cases().get(0).withNeedsReview(true))),
        draft.version(),
        "fixture",
        "editar-caso",
        List.of());
    mockMvc
        .perform(get(DRAFT).cookie(admin()))
        .andExpect(jsonPath("$.caseResults[0].status").value("por_revisar"));

    String body =
        """
        {"testCase":{"id":"c01a-no-oculta-c01b","nombre":"C01A en No oculta C01B","pagina":"C1",
          "respuestas":{"C01A":"2"},"esperado":{"ocultas":["C01B"]}},"version":2}""";
    send(put(DRAFT + "/cases/c01a-no-oculta-c01b"), body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseResults[0].status").value("pasa"));
    assertThat(rulesFiles.load("EDIN", 2027).cases().get(0).isNeedsReview()).isFalse();

    send(put(DRAFT + "/cases/c01a-si-caso"), CASE_BODY.formatted(3))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseResults.length()").value(2));
    send(delete(DRAFT + "/cases/c01a-si-caso?version=4"), null).andExpect(status().isOk());
    send(delete(DRAFT + "/cases/c01a-si-caso?version=5"), null).andExpect(status().isNotFound());
    assertThat(rulesFiles.load("EDIN", 2027).cases()).hasSize(1);
  }

  @Test
  void staleVersionGets409() throws Exception {
    send(put(DRAFT + "/skips/c01a-si-oculta-c01b"), SKIP_C01A_YES.formatted(1))
        .andExpect(status().isOk());
    send(put(DRAFT + "/skips/c01a-si-oculta-c01b"), SKIP_C01A_YES.formatted(1))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("Otro usuario modificó el cuestionario. Recargue la página."));
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(2);
  }

  @Test
  void publishedYearRulesNeedAReasonForEveryChange() throws Exception {
    int versionBefore = rulesFiles.load("EDIN", 2026).version();
    send(put(PUBLISHED + "/skips/c01a-si-oculta-c01b"), SKIP_C01A_YES.formatted(2))
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.detail")
                .value("Indique el motivo de la corrección (entre 10 y 500 caracteres)."));
    send(delete(PUBLISHED + "/cases/c01a-no-oculta-c01b?version=2"), null)
        .andExpect(status().isUnprocessableContent());
    assertThat(rulesFiles.load("EDIN", 2026).version()).isEqualTo(versionBefore);
    mockMvc.perform(get(PUBLISHED).cookie(admin())).andExpect(status().isOk());
  }

  @Test
  void pathIdMismatchIs422() throws Exception {
    send(put(DRAFT + "/skips/otro-id"), SKIP_C01A_YES.formatted(1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("El id de la regla no coincide."));
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void deleteMissingRuleIs404() throws Exception {
    send(delete(DRAFT + "/skips/no-existe?version=1"), null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("La regla no existe."));
  }

  @Test
  void respondentAndNationalAdminGet403() throws Exception {
    Cookie respondent = session(SessionPrincipal.respondent("1000000001"));
    Cookie national =
        session(SessionPrincipal.administrator("adm.nac", EnumSet.of(Role.ADMIN_NACIONAL), null));
    for (Cookie cookie : new Cookie[] {respondent, national}) {
      mockMvc.perform(get(DRAFT).cookie(cookie)).andExpect(status().isForbidden());
      mockMvc
          .perform(
              put(DRAFT + "/skips/c01a-si-oculta-c01b")
                  .with(realCsrf(mockMvc))
                  .cookie(cookie)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(SKIP_C01A_YES.formatted(1)))
          .andExpect(status().isForbidden());
    }
    mockMvc.perform(get(DRAFT)).andExpect(status().isUnauthorized());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void writesRequireCsrf() throws Exception {
    mockMvc
        .perform(
            put(DRAFT + "/skips/c01a-si-oculta-c01b")
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(SKIP_C01A_YES.formatted(1)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete(DRAFT + "/cases/c01a-no-oculta-c01b?version=1").cookie(admin()))
        .andExpect(status().isForbidden());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void bodyWithoutVersionIs400() throws Exception {
    send(
            put(DRAFT + "/skips/c01a-si-oculta-c01b"),
            """
            {"rule":{"id":"c01a-si-oculta-c01b","si":{"pregunta":"C01A","op":"eq","valor":"1"},
              "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}}}""")
        .andExpect(status().isBadRequest());
  }

  private static String importOf(String skipId, String caseId) {
    return """
        {"rules":{"encuesta":"EDIN","anio":2027,"version":0,"saltos":[
          {"id":"%s","si":{"pregunta":"C01A","op":"eq","valor":"1"},
           "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}}],
         "validaciones":{},
         "casos":[{"id":"%s","nombre":"Caso","pagina":"C1","respuestas":{"C01A":"2"},
           "esperado":{"ocultas":["C01B"]}}]},"version":1}"""
        .formatted(skipId, caseId);
  }

  @Test
  void importWithIdBeyondAuditLimitIs422() throws Exception {
    String tooLong = "a".repeat(41);
    for (String body :
        new String[] {
          importOf(tooLong, "caso-ok"),
          importOf("regla.1", "caso-ok"),
          importOf("regla con espacios", "caso-ok"),
          importOf("regla-ok", tooLong),
          importOf("regla-ok", "caso.1")
        }) {
      send(put(DRAFT + "/import"), body).andExpect(status().isUnprocessableContent());
    }
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void importKeepsMixedCaseLegacyIds() throws Exception {
    send(put(DRAFT + "/import"), importOf("legacy-C01A", "caso-ok")).andExpect(status().isOk());
  }

  @Test
  void newIdOfFortyCharactersIsAcceptedAndFortyOneRejected() throws Exception {
    String forty = "a".repeat(40);
    String rule =
        """
        {"rule":{"id":"%s","si":{"pregunta":"C01A","op":"eq","valor":"1"},
          "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},"version":%d}""";
    send(put(DRAFT + "/skips/" + "a".repeat(41)), rule.formatted("a".repeat(41), 1))
        .andExpect(status().isUnprocessableContent());
    send(put(DRAFT + "/skips/" + forty), rule.formatted(forty, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    send(delete(DRAFT + "/skips/" + forty + "?version=2"), null).andExpect(status().isOk());
    String testCase =
        """
        {"testCase":{"id":"%s","nombre":"Caso","pagina":"C1","respuestas":{},
          "esperado":{"ocultas":[]}},"version":%d}""";
    send(put(DRAFT + "/cases/" + "b".repeat(41)), testCase.formatted("b".repeat(41), 3))
        .andExpect(status().isUnprocessableContent());
    send(put(DRAFT + "/cases/" + "b".repeat(40)), testCase.formatted("b".repeat(40), 3))
        .andExpect(status().isOk());
  }

  @Test
  void importLargerThanOneMegabyteIs413() throws Exception {
    String body =
        importOf("regla-ok", "caso-ok").replace("\"Caso\"", "\"" + "x".repeat(1_100_000) + "\"");
    send(put(DRAFT + "/import"), body)
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.detail").value("El archivo supera 1 MB."));
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void importWithStructuralProblemsIs422Estructura() throws Exception {
    String longName =
        importOf("regla-ok", "caso-ok").replace("\"Caso\"", "\"" + "n".repeat(101) + "\"");
    send(put(DRAFT + "/import"), longName)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.report.errors[0].code").value("ESTRUCTURA"));
    String duplicated =
        """
        {"rules":{"encuesta":"EDIN","anio":2027,"version":0,"saltos":[],"validaciones":{},
         "casos":[
          {"id":"caso-1","nombre":"Uno","pagina":"C1","respuestas":{},"esperado":{}},
          {"id":"caso-1","nombre":"Dos","pagina":"C1","respuestas":{},"esperado":{}}]},
         "version":1}""";
    send(put(DRAFT + "/import"), duplicated)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.report.errors[0].code").value("ESTRUCTURA"));
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void malformedImportJsonIs400() throws Exception {
    send(put(DRAFT + "/import"), "{\"rules\":").andExpect(status().isBadRequest());
  }

  @Test
  void importWithoutCsrfIs403() throws Exception {
    mockMvc
        .perform(
            put(DRAFT + "/import")
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(importOf("regla-ok", "caso-ok")))
        .andExpect(status().isForbidden());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void nullsInsideASkipAreRejectedAs422Estructura() throws Exception {
    String[] conditions = {
      "{\"y\":[null]}",
      "{\"o\":[{\"pregunta\":\"C01A\",\"op\":\"eq\",\"valor\":\"1\"},null]}",
      "{\"pregunta\":\"C01A\",\"op\":\"in\",\"valores\":[null]}",
      "{\"todas\":[null],\"op\":\"eq\",\"valor\":\"1\"}",
    };
    for (String condition : conditions) {
      String body =
          """
          {"rule":{"id":"con-nulos","si":%s,
            "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},"version":1}"""
              .formatted(condition);
      send(put(DRAFT + "/skips/con-nulos"), body)
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.report.errors[0].code").value("ESTRUCTURA"));
    }
    String nullTarget =
        """
        {"rule":{"id":"con-nulos","si":{"pregunta":"C01A","op":"eq","valor":"1"},
          "entonces":{"accion":"deshabilitar","preguntas":[null]}},"version":1}""";
    send(put(DRAFT + "/skips/con-nulos"), nullTarget)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.report.errors[0].code").value("ESTRUCTURA"));
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void nullsInsideACaseAreRejectedAs422Estructura() throws Exception {
    String[] parts = {
      "\"respuestas\":{},\"esperado\":{\"ocultas\":[null]}",
      "\"respuestas\":{},\"esperado\":{\"ocultas\":[null,\"C01B\",null]}",
      "\"respuestas\":{\"C01A\":null},\"esperado\":{\"ocultas\":[]}",
      "\"respuestas\":{},\"esperado\":{\"ocultas\":[],\"errores\":{\"C01A\":null}}",
    };
    for (String part : parts) {
      String body =
          """
          {"testCase":{"id":"caso-nulos","nombre":"Caso con nulos","pagina":"C1",%s},
            "version":1}"""
              .formatted(part);
      send(put(DRAFT + "/cases/caso-nulos"), body)
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.report.errors[0].code").value("ESTRUCTURA"));
    }
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void importWithNullsIsRejectedAs422Estructura() throws Exception {
    String[] bodies = {
      importOf("regla-ok", "caso-ok")
          .replace(
              "\"si\":{\"pregunta\":\"C01A\",\"op\":\"eq\",\"valor\":\"1\"}",
              "\"si\":{\"y\":[null]}"),
      importOf("regla-ok", "caso-ok").replace("\"ocultas\":[\"C01B\"]", "\"ocultas\":[null]"),
      importOf("regla-ok", "caso-ok").replace("\"C01A\":\"2\"", "\"C01A\":null"),
    };
    for (String body : bodies) {
      send(put(DRAFT + "/import"), body)
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.report.errors[0].code").value("ESTRUCTURA"));
    }
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void ruleWhoseAnalysisIsNotAnalyzableIsSaved() throws Exception {
    // Nueve combos con tres respuestas distinguibles cada uno: 3^9 combinaciones, más de 4096.
    String combos =
        "[\"B02\",\"C01A\",\"C13\",\"D01A\",\"D09\",\"E01A\",\"F01A\",\"G01A\",\"H01\"]";
    String body =
        """
        {"rule":{"id":"muchas-preguntas","si":{"o":[
            {"alguna":%s,"op":"eq","valor":"1"},{"alguna":%s,"op":"ne","valor":"1"}]},
          "entonces":{"accion":"deshabilitar","preguntas":["D05"]}},"version":1}"""
            .formatted(combos, combos);

    send(put(DRAFT + "/skips/muchas-preguntas"), body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(
            jsonPath(
                    "$.report.warnings[?(@.code == 'NO_ANALIZABLE'"
                        + " && @.element == 'regla:muchas-preguntas')]")
                .exists());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(2);
  }

  /** Filas de todas las tablas de respuestas de EDIN: el simulador nunca debe tocarlas. */
  private long answerRows() {
    long total = 0;
    for (String table :
        jdbc.queryForList(
            "SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME LIKE 'EDIN#_20#_CAP#_%'"
                + " ESCAPE '#'",
            String.class)) {
      total += jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
    return total;
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
