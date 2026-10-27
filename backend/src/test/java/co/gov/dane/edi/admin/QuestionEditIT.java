package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static co.gov.dane.edi.support.DraftYearFixture.draftCapProy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.rules.Condition;
import co.gov.dane.edi.rules.RuleSet;
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
import java.util.Map;
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

@ExtendWith(OutputCaptureExtension.class)
class QuestionEditIT extends OracleIntegrationTest {

  private static final String DRAFT = "/api/v1/admin/questionnaire/EDIN/2027";
  private static final String PUBLISHED = "/api/v1/admin/questionnaire/EDIN/2026";
  private static final long C01A = 301;
  private static final long C01B = 302;

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private SurveyProperties survey;
  @Autowired private QuestionnaireService questionnaire;

  private DraftYearFixture fixture;

  @BeforeEach
  void installDraftYear() {
    fixture = new DraftYearFixture(jdbc, rulesFiles, survey);
    fixture.install();
    questionnaire.evict();
  }

  @AfterEach
  void removeDraftYear() {
    fixture.remove();
    questionnaire.evict();
  }

  @Test
  void editsTextOfDraftYear(CapturedOutput output) throws Exception {
    edit(
            DRAFT + "/entries/" + draftCapProy(C01A) + "/text",
            "{\"text\":\"Texto nuevo de C01A\",\"version\":1}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.page.pageCode").value("C1"))
        .andExpect(
            jsonPath("$.page.entries[?(@.columnCode == 'C01A')].text").value("Texto nuevo de C01A"))
        .andExpect(jsonPath("$.skipsTargetingEarlierQuestions.length()").value(0));

    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(2);
    // La pregunta era compartida con 2026: 2027 tiene ahora la suya y 2026 conserva la original.
    assertThat(textOfQuestion(201)).isEqualTo("&iquest;Pregunta ficticia C01A?");
    assertThat(output.getAll())
        .contains("operacion=editar-texto", "encuesta=EDIN", "anio=2027", "codigos=[C01A]")
        .doesNotContain("Texto nuevo de C01A");
  }

  @Test
  void editsHeaderAndHelpOfDraftYear() throws Exception {
    long header = DraftYearFixture.OFFSET + 1;
    long help = DraftYearFixture.OFFSET + 1;
    edit(DRAFT + "/headers/" + header, "{\"text\":\"Encabezado nuevo\",\"version\":1}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(
            jsonPath("$.page.entries[0].headers[?(@.codEnc == " + header + ")].text")
                .value("Encabezado nuevo"));
    edit(DRAFT + "/help/" + help, "{\"text\":\"Ayuda nueva\",\"version\":2}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3))
        .andExpect(jsonPath("$.page.entries[0].help.text").value("Ayuda nueva"));

    assertThat(
            jdbc.queryForObject(
                "SELECT ENCABEZADO FROM FRM_ENCABEZADO WHERE COD_ENC = 1", String.class))
        .isEqualTo("Ubicaci&oacute;n laboral");
    assertThat(
            jdbc.queryForObject("SELECT TEXTO FROM FRM_PRE_AYUDA WHERE COD_AYU = 1", String.class))
        .isEqualTo("Ayuda ficticia de la página B1");
  }

  @Test
  void editingDraftYearLeavesOtherYearsUntouched() throws Exception {
    SurveyPage c1 = SurveyPage.find("C1").orElseThrow();
    SurveyPage b1 = SurveyPage.find("B1").orElseThrow();
    List<QuestionnaireEntry> c1Before = questionnaire.pageEntries(c1, "EDIN", 2026);
    List<QuestionnaireEntry> b1Before = questionnaire.pageEntries(b1, "EDIN", 2026);
    List<Integer> ordersBefore = ordersOfGroups(201, 202);

    edit(
            DRAFT + "/entries/" + draftCapProy(C01A) + "/text",
            "{\"text\":\"Solo para 2027\",\"version\":1}")
        .andExpect(status().isOk());
    edit(
            DRAFT + "/headers/" + (DraftYearFixture.OFFSET + 1),
            "{\"text\":\"Encabezado 2027\",\"version\":2}")
        .andExpect(status().isOk());
    reorder(DRAFT + "/pages/C1/order", "[" + draftCapProy(C01B) + "," + draftCapProy(C01A) + "]", 3)
        .andExpect(status().isOk());

    assertThat(questionnaire.pageEntries(c1, "EDIN", 2026)).isEqualTo(c1Before);
    assertThat(questionnaire.pageEntries(b1, "EDIN", 2026)).isEqualTo(b1Before);
    assertThat(ordersOfGroups(201, 202)).isEqualTo(ordersBefore);
    assertThat(
            questionnaire.pageEntries(c1, "EDIN", 2027).stream()
                .map(entry -> entry.answer().columnCode())
                .toList())
        .containsExactly("C01B", "C01A");
    assertThat(questionnaire.pageEntries(c1, "EDIN", 2027).get(1).text())
        .isEqualTo("Solo para 2027");
  }

  @Test
  void reorderReportsSkipsTargetingEarlierQuestions() throws Exception {
    RuleSet draft = rulesFiles.load("EDIN", 2027);
    SkipRule rule =
        new SkipRule(
            "regla-c01a",
            "Si C01A es No se deshabilita C01B",
            new Condition("C01A", "eq", "2", null, null, null, null, null),
            new SkipAction("deshabilitar", List.of("C01B")));
    rulesFiles.save(
        new RuleSet(
            "EDIN",
            2027,
            draft.version(),
            List.of(rule),
            draft.validations(),
            draft.state(),
            Map.of()),
        draft.version(),
        "fixture",
        "agregar-regla-prueba",
        List.of());

    reorder(DRAFT + "/pages/C1/order", "[" + draftCapProy(C01B) + "," + draftCapProy(C01A) + "]", 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.entries[0].columnCode").value("C01B"))
        .andExpect(jsonPath("$.skipsTargetingEarlierQuestions[0]").value("regla-c01a"));
    reorder(DRAFT + "/pages/C1/order", "[" + draftCapProy(C01A) + "," + draftCapProy(C01B) + "]", 3)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skipsTargetingEarlierQuestions.length()").value(0));
  }

  @Test
  void staleEditIsRejectedWithConflict() throws Exception {
    String path = DRAFT + "/entries/" + draftCapProy(C01A) + "/text";
    edit(path, "{\"text\":\"Primera pestaña\",\"version\":1}").andExpect(status().isOk());
    edit(path, "{\"text\":\"Segunda pestaña\",\"version\":1}")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("Otro usuario modificó el cuestionario. Recargue la página."));

    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(2);
    assertThat(questionnaire.pageEntries(SurveyPage.find("C1").orElseThrow(), "EDIN", 2027).get(0))
        .extracting(QuestionnaireEntry::text)
        .isEqualTo("Primera pestaña");
  }

  @Test
  void publishedYearIsReadOnly() throws Exception {
    edit(PUBLISHED + "/entries/" + C01A + "/text", "{\"text\":\"Intento\",\"version\":1}")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("El cuestionario de 2026 está publicado y no se puede editar."));
    reorder(PUBLISHED + "/pages/C1/order", "[" + C01B + "," + C01A + "]", 1)
        .andExpect(status().isConflict());
    assertThat(textOfQuestion(201)).isEqualTo("&iquest;Pregunta ficticia C01A?");
  }

  @Test
  void entryOfAnotherYearCannotBeEditedThroughTheDraftPath() throws Exception {
    edit(DRAFT + "/entries/" + C01A + "/text", "{\"text\":\"Intento\",\"version\":1}")
        .andExpect(status().isNotFound());
    edit(DRAFT + "/headers/3", "{\"text\":\"Intento\",\"version\":1}")
        .andExpect(status().isNotFound());
    edit(DRAFT + "/entries/999999/text", "{\"text\":\"Intento\",\"version\":1}")
        .andExpect(status().isNotFound());
    reorder(DRAFT + "/pages/Z9/order", "[1]", 1).andExpect(status().isNotFound());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void reorderRejectsMismatchedList() throws Exception {
    String detail = "La lista no coincide con las preguntas de la página.";
    reorder(DRAFT + "/pages/C1/order", "[" + draftCapProy(C01A) + "]", 1)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(detail));
    reorder(DRAFT + "/pages/C1/order", "[" + draftCapProy(C01A) + "," + draftCapProy(C01A) + "]", 1)
        .andExpect(status().isUnprocessableContent());
    // Una pregunta de otra página no pertenece a esta.
    reorder(DRAFT + "/pages/C1/order", "[" + draftCapProy(C01A) + "," + draftCapProy(303) + "]", 1)
        .andExpect(status().isUnprocessableContent());
    reorder(DRAFT + "/pages/C1/order", "[]", 1).andExpect(status().isBadRequest());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void textIsStoredPlainAndRenderedEscaped() throws Exception {
    String path = DRAFT + "/entries/" + draftCapProy(C01A) + "/text";
    edit(path, "{\"text\":\"<script>x</script>&amp;\\u0007\",\"version\":1}")
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.page.entries[?(@.columnCode == 'C01A')].text")
                .value("<script>x</script>&amp;"))
        .andExpect(
            jsonPath("$.page.entries[?(@.columnCode == 'C01A')].rawText")
                .value("<script>x</script>&amp;"));
    assertThat(storedTextOf(draftCapProy(C01A)))
        .isEqualTo("&lt;script&gt;x&lt;/script&gt;&amp;amp;");
    SurveyPage c1 = SurveyPage.find("C1").orElseThrow();
    assertThat(questionnaire.pageEntries(c1, "EDIN", 2027).get(0).text())
        .isEqualTo("<script>x</script>&amp;");

    edit(path, "{\"text\":\"Ingreso <entre 1 y 2> SMMLV\",\"version\":2}")
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.page.entries[?(@.columnCode == 'C01A')].text")
                .value("Ingreso <entre 1 y 2> SMMLV"));
    assertThat(questionnaire.pageEntries(c1, "EDIN", 2027).get(0).text())
        .isEqualTo("Ingreso <entre 1 y 2> SMMLV");
  }

  @Test
  void blankOrOversizedTextIsRejected() throws Exception {
    String path = DRAFT + "/entries/" + draftCapProy(C01A) + "/text";
    edit(path, "{\"text\":\"\\u0007\\u0008\",\"version\":1}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.text").value("El texto no puede quedar vacío."));
    edit(path, "{\"text\":\"" + "&".repeat(1000) + "\",\"version\":1}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.text").value("El texto es demasiado largo."));
    edit(path, "{\"text\":\"  \",\"version\":1}").andExpect(status().isBadRequest());
    edit(path, "{\"text\":\"Hola\"}").andExpect(status().isBadRequest());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void yearWildcardSurvivesAnEdit() throws Exception {
    jdbc.update("UPDATE FRM_PREGUNTA SET PREGUNTA = 'Ingreso del 200X' WHERE COD_PRE = 201");
    try {
      questionnaire.evict();
      mockMvc
          .perform(get(DRAFT).cookie(admin()))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.pages[?(@.pageCode == 'C1')].entries[?(@.columnCode == 'C01A')].text")
                  .value("Ingreso del 2027"))
          .andExpect(
              jsonPath("$.pages[?(@.pageCode == 'C1')].entries[?(@.columnCode == 'C01A')].rawText")
                  .value("Ingreso del 200X"));

      edit(
              DRAFT + "/entries/" + draftCapProy(C01A) + "/text",
              "{\"text\":\"Gasto del 200X\",\"version\":1}")
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.page.entries[?(@.columnCode == 'C01A')].text").value("Gasto del 2027"))
          .andExpect(
              jsonPath("$.page.entries[?(@.columnCode == 'C01A')].rawText")
                  .value("Gasto del 200X"));
      assertThat(storedTextOf(draftCapProy(C01A))).isEqualTo("Gasto del 200X");
    } finally {
      jdbc.update(
          "UPDATE FRM_PREGUNTA SET PREGUNTA = '&iquest;Pregunta ficticia C01A?'"
              + " WHERE COD_PRE = 201");
    }
  }

  @Test
  void reordersWithinAGroupBlock() throws Exception {
    long first = draftCapProy(309);
    long second = DraftYearFixture.H1_GROUP_1_SECOND;
    long third = DraftYearFixture.H1_GROUP_2_FIRST;
    long fourth = DraftYearFixture.H1_GROUP_2_SECOND;
    reorder(
            DRAFT + "/pages/H1/order",
            "[" + second + "," + first + "," + fourth + "," + third + "]",
            1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.page.entries[0].capProyId").value(second))
        .andExpect(jsonPath("$.page.entries[1].capProyId").value(first))
        .andExpect(jsonPath("$.page.entries[2].capProyId").value(fourth))
        .andExpect(jsonPath("$.page.entries[3].capProyId").value(third));
  }

  @Test
  void reorderAcrossGroupsIsRejected() throws Exception {
    long first = draftCapProy(309);
    long second = DraftYearFixture.H1_GROUP_1_SECOND;
    long third = DraftYearFixture.H1_GROUP_2_FIRST;
    long fourth = DraftYearFixture.H1_GROUP_2_SECOND;
    reorder(
            DRAFT + "/pages/H1/order",
            "[" + third + "," + first + "," + second + "," + fourth + "]",
            1)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("No se puede mover una pregunta a otro grupo."));
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
    SurveyPage h1 = SurveyPage.find("H1").orElseThrow();
    assertThat(questionnaire.pageRows(h1, "EDIN", 2027).get(0).row().entryId()).isEqualTo(first);
  }

  @Test
  void csrfRequired() throws Exception {
    mockMvc
        .perform(
            put(DRAFT + "/entries/" + draftCapProy(C01A) + "/text")
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Sin token\",\"version\":1}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch(DRAFT + "/pages/C1/order")
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"capProyIds\":[1],\"version\":1}"))
        .andExpect(status().isForbidden());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void otherRolesCannotEdit() throws Exception {
    Cookie respondent = session(SessionPrincipal.respondent("1000000001"));
    Cookie national =
        session(SessionPrincipal.administrator("adm.nac", EnumSet.of(Role.ADMIN_NACIONAL), null));
    for (Cookie cookie : new Cookie[] {respondent, national}) {
      mockMvc
          .perform(
              put(DRAFT + "/entries/" + draftCapProy(C01A) + "/text")
                  .with(realCsrf(mockMvc))
                  .cookie(cookie)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"text\":\"Intento\",\"version\":1}"))
          .andExpect(status().isForbidden());
    }
  }

  private ResultActions edit(String path, String body) throws Exception {
    return mockMvc.perform(request(put(path), body));
  }

  private ResultActions reorder(String path, String ids, int version) throws Exception {
    return mockMvc.perform(
        request(patch(path), "{\"capProyIds\":" + ids + ",\"version\":" + version + "}"));
  }

  private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder, String body)
      throws Exception {
    return builder
        .with(realCsrf(mockMvc))
        .cookie(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private String storedTextOf(long capProyId) {
    return jdbc.queryForObject(
        "SELECT P.PREGUNTA FROM FRM_PREGUNTA P JOIN FRM_PRE_GRUPO G ON G.COD_PRE = P.COD_PRE"
            + " JOIN FRM_CAP_PROY C ON C.COD_PRE_GRUPO = G.COD_PRE_GRUPO"
            + " WHERE C.COD_CAP_PROY = ?",
        String.class,
        capProyId);
  }

  private String textOfQuestion(long codPre) {
    return jdbc.queryForObject(
        "SELECT PREGUNTA FROM FRM_PREGUNTA WHERE COD_PRE = ?", String.class, codPre);
  }

  private List<Integer> ordersOfGroups(long... groups) {
    return java.util.Arrays.stream(groups)
        .mapToObj(
            group ->
                jdbc.queryForObject(
                    "SELECT ORDEN FROM FRM_PRE_GRUPO WHERE COD_PRE_GRUPO = ?",
                    Integer.class,
                    group))
        .toList();
  }

  private Cookie admin() {
    return session(
        SessionPrincipal.administrator("adm.cuest", EnumSet.of(Role.ADMIN_CUESTIONARIO), null));
  }

  private Cookie session(SessionPrincipal principal) {
    return new Cookie(SessionTokenService.SESSION_COOKIE_NAME, tokens.issueToken(principal));
  }
}
