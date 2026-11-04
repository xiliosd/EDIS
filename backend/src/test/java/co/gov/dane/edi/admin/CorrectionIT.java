package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
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

/** Correcciones con motivo sobre un año publicado (2027, publicado con el fixture). */
@ExtendWith(OutputCaptureExtension.class)
class CorrectionIT extends OracleIntegrationTest {

  private static final String YEAR = "/api/v1/admin/questionnaire/EDIN/2027";
  private static final String RULES = YEAR + "/rules";
  private static final long C01A = 301;
  private static final long C01B = 302;
  private static final String REASON = "Se corrige la redacción según el comité técnico";
  private static final String NEEDS_REASON =
      "Indique el motivo de la corrección (entre 10 y 500 caracteres).";
  private static final String STRUCTURAL_MESSAGE =
      "El cuestionario de 2027 está publicado: solo se permiten correcciones de textos, saltos y"
          + " validaciones.";

  /** Regla inocua: el caso existente (C01A en No) sigue pasando. */
  private static final String HARMLESS_SKIP =
      """
      {"rule":{"id":"c01a-si-oculta-c01b","si":{"pregunta":"C01A","op":"eq","valor":"1"},
        "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},"version":%d,"motivo":"%s"}""";

  /** Oculta B06 cuando C01A es No: el caso existente esperaba solo C01B oculta y pasa a fallar. */
  private static final String SKIP_THAT_BREAKS_THE_CASE =
      """
      {"rule":{"id":"c01a-no-oculta-b06","si":{"pregunta":"C01A","op":"eq","valor":"2"},
        "entonces":{"accion":"deshabilitar","preguntas":["B06"]}},"version":%d,"motivo":"%s"}""";

  private static final String SKIP_TO_UNKNOWN_QUESTION =
      """
      {"rule":{"id":"apunta-a-z99","si":{"pregunta":"Z99","op":"eq","valor":"1"},
        "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},"version":%d,"motivo":"%s"}""";

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
  void publishedYearAllowsOnlyCorrectionsWithReason() throws Exception {
    fixture.installPublished("fixture");
    long c01a = fixture.draft().draftCapProy(C01A);
    long c01b = fixture.draft().draftCapProy(C01B);
    RuleSet published = rulesFiles.load("EDIN", 2027);
    assertThat(published.version()).isEqualTo(2);
    List<Long> counts = frmCounts();

    // Estructurales: 409 aunque lleguen con motivo.
    send(
            post(YEAR + "/pages/C1/entries"),
            "{\"columnCode\":\"C30A\",\"numeral\":\"C30A.\",\"text\":\"x\",\"inputType\":\"TEXT\","
                + "\"version\":2,\"motivo\":\""
                + REASON
                + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(STRUCTURAL_MESSAGE));
    send(
            delete(YEAR + "/entries/" + c01a + "?version=2")
                .param("motivo", "Motivo de prueba largo"),
            null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(STRUCTURAL_MESSAGE));
    send(
            patch(YEAR + "/pages/C1/order"),
            "{\"capProyIds\":["
                + c01b
                + ","
                + c01a
                + "],\"version\":2,\"motivo\":\""
                + REASON
                + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(STRUCTURAL_MESSAGE));
    send(
            put(YEAR + "/tables"),
            "{\"tables\":{\"B\":\"FRM_B_NUEVA\"},\"version\":2,\"motivo\":\"" + REASON + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(STRUCTURAL_MESSAGE));
    assertUntouched(published, counts);

    // Corrección sin motivo, con motivo corto o demasiado largo: 422.
    String path = YEAR + "/entries/" + c01a + "/text";
    for (String body :
        List.of(
            "{\"text\":\"Texto corregido\",\"version\":2}",
            "{\"text\":\"Texto corregido\",\"version\":2,\"motivo\":\"   \"}",
            "{\"text\":\"Texto corregido\",\"version\":2,\"motivo\":\"corto\"}",
            "{\"text\":\"Texto corregido\",\"version\":2,\"motivo\":\""
                + "x".repeat(501)
                + "\"}")) {
      send(put(path), body)
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.detail").value(NEEDS_REASON));
    }
    assertUntouched(published, counts);

    // Corrección válida: 200, versión + 1 y el sello guarda motivo y operación.
    send(put(path), textBody("Texto corregido", 2, "  " + REASON + "  "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3))
        .andExpect(
            jsonPath("$.page.entries[?(@.columnCode == 'C01A')].text").value("Texto corregido"));
    RuleSet corrected = rulesFiles.load("EDIN", 2027);
    assertThat(corrected.version()).isEqualTo(3);
    assertThat(corrected.effectiveState()).isEqualTo(YearState.PUBLICADO);
    assertThat(corrected.lastChange().operation()).isEqualTo("corregir-editar-texto");
    assertThat(corrected.lastChange().reason()).isEqualTo(REASON);
  }

  @Test
  void headerHelpSkipValidationAndCaseCorrectionsKeepTheirOwnOperation() throws Exception {
    fixture.installPublished("fixture");
    long header = fixture.draft().draftHeader(1);
    long help = fixture.draft().draftHelp(1);

    send(put(YEAR + "/headers/" + header), textBody("Encabezado corregido", 2, REASON))
        .andExpect(status().isOk());
    assertThat(operation()).isEqualTo("corregir-editar-encabezado");
    send(put(YEAR + "/help/" + help), textBody("Ayuda corregida", 3, REASON))
        .andExpect(status().isOk());
    assertThat(operation()).isEqualTo("corregir-editar-ayuda");

    send(put(RULES + "/skips/c01a-si-oculta-c01b"), HARMLESS_SKIP.formatted(4, REASON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(5))
        .andExpect(jsonPath("$.caseResults[0].status").value("pasa"));
    assertThat(operation()).isEqualTo("corregir-editar-salto");

    send(put(RULES + "/skips/c01a-si-oculta-c01b"), HARMLESS_SKIP.formatted(5, "corto"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(NEEDS_REASON));
    send(delete(RULES + "/skips/c01a-si-oculta-c01b?version=5"), null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(NEEDS_REASON));
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(5);

    send(
            delete(RULES + "/skips/c01a-si-oculta-c01b?version=5")
                .param("motivo", "Se retira la regla"),
            null)
        .andExpect(status().isOk());
    assertThat(operation()).isEqualTo("corregir-borrar-salto");
    assertThat(rulesFiles.load("EDIN", 2027).lastChange().reason()).isEqualTo("Se retira la regla");

    send(
            put(RULES + "/validations/B06"),
            "{\"validation\":{\"tipo\":\"entero\",\"min\":0,\"max\":99},\"version\":6,\"motivo\":\""
                + REASON
                + "\"}")
        .andExpect(status().isOk());
    assertThat(operation()).isEqualTo("corregir-editar-validacion");
    send(
            delete(RULES + "/validations/B06?version=7").param("motivo", "Se retira la validacion"),
            null)
        .andExpect(status().isOk());
    assertThat(operation()).isEqualTo("corregir-borrar-validacion");

    send(
            put(RULES + "/cases/c01a-si-caso"),
            "{\"testCase\":{\"id\":\"c01a-si-caso\",\"nombre\":\"C01A en Si no oculta C01B\","
                + "\"pagina\":\"C1\",\"respuestas\":{\"C01A\":\"1\"},"
                + "\"esperado\":{\"ocultas\":[]}},\"version\":8,\"motivo\":\""
                + REASON
                + "\"}")
        .andExpect(status().isOk());
    assertThat(operation()).isEqualTo("corregir-editar-caso");
    send(
            delete(RULES + "/cases/c01a-si-caso?version=9").param("motivo", "Se retira el caso ok"),
            null)
        .andExpect(status().isOk());
    assertThat(operation()).isEqualTo("corregir-borrar-caso");
  }

  @Test
  void correctionCannotIntroduceErrors() throws Exception {
    fixture.installPublished("fixture");
    RuleSet published = rulesFiles.load("EDIN", 2027);

    send(put(RULES + "/skips/apunta-a-z99"), SKIP_TO_UNKNOWN_QUESTION.formatted(2, REASON))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.report.errors.length()").value(1));

    assertThat(rulesFiles.load("EDIN", 2027)).isEqualTo(published);
  }

  @Test
  void correctionThatTurnsACaseRedIsRejected(CapturedOutput output) throws Exception {
    fixture.installPublished("fixture");
    RuleSet published = rulesFiles.load("EDIN", 2027);

    send(put(RULES + "/skips/c01a-no-oculta-b06"), SKIP_THAT_BREAKS_THE_CASE.formatted(2, REASON))
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.detail")
                .value("La corrección hace fallar casos de prueba: c01a-no-oculta-c01b."));

    assertThat(rulesFiles.load("EDIN", 2027)).isEqualTo(published);
    assertThat(output.getAll()).doesNotContain(REASON);
  }

  @Test
  void textCorrectionMayLeaveCasesForReview() throws Exception {
    fixture.installPublished("fixture");

    send(
            put(YEAR + "/entries/" + fixture.draft().draftCapProy(C01A) + "/text"),
            textBody("Texto corregido de C01A", 2, REASON))
        .andExpect(status().isOk());

    assertThat(rulesFiles.load("EDIN", 2027).cases())
        .isNotEmpty()
        .allMatch(testCase -> testCase.isNeedsReview());
  }

  @Test
  void reasonNeverReachesTheLogs(CapturedOutput output) throws Exception {
    fixture.installPublished("fixture");
    String secret = "MOTIVO-CONFIDENCIAL-4711 de la corrección";

    send(
            put(YEAR + "/entries/" + fixture.draft().draftCapProy(C01A) + "/text"),
            textBody("Texto corregido", 2, secret))
        .andExpect(status().isOk());
    send(
            delete(RULES + "/skips/no-existe?version=3")
                .param("motivo", "MOTIVO-CONFIDENCIAL-4711 x"),
            null)
        .andExpect(status().isNotFound());

    assertThat(output.getAll())
        .contains("operacion=corregir-editar-texto")
        .doesNotContain("MOTIVO-CONFIDENCIAL");
    assertThat(rulesFiles.load("EDIN", 2027).lastChange().reason()).isEqualTo(secret);
  }

  @Test
  void draftEditsStillNeedNoReason() throws Exception {
    fixture.installDraft();
    String path = YEAR + "/entries/" + fixture.draft().draftCapProy(C01A) + "/text";

    send(put(path), "{\"text\":\"Texto de borrador\",\"version\":1}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    assertThat(operation()).isEqualTo("editar-texto");
    assertThat(rulesFiles.load("EDIN", 2027).lastChange().reason()).isNull();

    // En un borrador el motivo se ignora: no queda guardado ni cambia la operación.
    send(put(path), textBody("Otro texto de borrador", 2, REASON)).andExpect(status().isOk());
    assertThat(operation()).isEqualTo("editar-texto");
    assertThat(rulesFiles.load("EDIN", 2027).lastChange().reason()).isNull();

    send(put(RULES + "/skips/c01a-si-oculta-c01b"), HARMLESS_SKIP.formatted(3, ""))
        .andExpect(status().isOk());
    assertThat(operation()).isEqualTo("editar-salto");
    send(delete(RULES + "/skips/c01a-si-oculta-c01b?version=4"), null).andExpect(status().isOk());
    assertThat(operation()).isEqualTo("borrar-salto");
  }

  private void assertUntouched(RuleSet expected, List<Long> counts) {
    assertThat(rulesFiles.load("EDIN", 2027)).isEqualTo(expected);
    assertThat(frmCounts()).isEqualTo(counts);
  }

  private List<Long> frmCounts() {
    List<Long> counts = new ArrayList<>();
    for (FrmTable table : FrmTable.values()) {
      counts.add(jdbc.queryForObject("SELECT COUNT(*) FROM " + table.tableName(), Long.class));
    }
    return counts;
  }

  private String operation() {
    return rulesFiles.load("EDIN", 2027).lastChange().operation();
  }

  private static String textBody(String text, int version, String reason) {
    return "{\"text\":\"" + text + "\",\"version\":" + version + ",\"motivo\":\"" + reason + "\"}";
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
