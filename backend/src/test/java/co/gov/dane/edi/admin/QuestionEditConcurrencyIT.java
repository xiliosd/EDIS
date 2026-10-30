package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import co.gov.dane.edi.admin.dto.AdminEntry;
import co.gov.dane.edi.admin.dto.AdminPage;
import co.gov.dane.edi.admin.dto.AdminQuestionnaireView;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.support.DraftYearFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Control optimista bajo concurrencia: la versión que recibe el administrador corresponde a la
 * página que recibe, y nadie lee una versión nueva antes de que sus filas estén confirmadas.
 */
class QuestionEditConcurrencyIT extends OracleIntegrationTest {

  private static final String DRAFT = "/api/v1/admin/questionnaire/EDIN/2027";
  private static final long C01A = 301;
  private static final long C01B = 302;
  private static final String PAUSED_EDITOR = "editor-pausado";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private SurveyProperties survey;
  @Autowired private YearCopyService yearCopy;
  @Autowired private QuestionnaireService questionnaire;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private FrmWriteRepository writes;
  @MockitoSpyBean private QuestionnaireAdminService view;

  private DraftYearFixture fixture;
  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  @BeforeEach
  void installDraftYear() {
    fixture = new DraftYearFixture(jdbc, rulesFiles, survey, yearCopy);
    fixture.install();
    questionnaire.evict();
  }

  @AfterEach
  void removeDraftYear() {
    executor.shutdownNow();
    fixture.remove();
    questionnaire.evict();
  }

  @Test
  void returnedVersionMatchesReturnedPageAndStaleEditsConflict() throws Exception {
    CountDownLatch paused = new CountDownLatch(1);
    CountDownLatch resume = new CountDownLatch(1);
    // El editor A se detiene justo al armar la página de su respuesta, ya confirmado su cambio.
    doAnswer(
            invocation -> {
              if (Thread.currentThread().getName().equals(PAUSED_EDITOR)) {
                paused.countDown();
                assertThat(resume.await(30, TimeUnit.SECONDS)).isTrue();
              }
              return invocation.callRealMethod();
            })
        .when(view)
        .page(anyString(), anyInt(), any(SurveyPage.class));

    Future<MvcResult> editorA =
        executor.submit(
            () -> {
              Thread.currentThread().setName(PAUSED_EDITOR);
              try {
                return editText(C01A, "Texto del editor A", 1);
              } finally {
                Thread.currentThread().setName("editor-a-terminado");
              }
            });
    assertThat(paused.await(30, TimeUnit.SECONDS)).isTrue();
    // B ya conoce la versión 2 (por ejemplo, la leyó del árbol tras el COMMIT de A).
    Future<MvcResult> editorB = executor.submit(() -> editText(C01B, "Texto del editor B", 2));
    Thread.sleep(500);
    assertThat(editorB.isDone())
        .as("B no puede guardar mientras A arma su respuesta bajo el candado")
        .isFalse();
    resume.countDown();

    JsonNode responseA = body(editorA.get(30, TimeUnit.SECONDS));
    assertThat(responseA.get("version").asInt()).isEqualTo(2);
    assertThat(textOf(responseA.get("page"), "C01A")).isEqualTo("Texto del editor A");
    assertThat(textOf(responseA.get("page"), "C01B")).isNotEqualTo("Texto del editor B");

    MvcResult resultB = editorB.get(30, TimeUnit.SECONDS);
    assertThat(resultB.getResponse().getStatus()).isEqualTo(200);
    JsonNode responseB = body(resultB);
    assertThat(responseB.get("version").asInt()).isEqualTo(3);
    assertThat(textOf(responseB.get("page"), "C01A")).isEqualTo("Texto del editor A");
    assertThat(textOf(responseB.get("page"), "C01B")).isEqualTo("Texto del editor B");

    // Quien aún tiene la versión 2 de A ya no puede pisar el cambio de B.
    assertThat(editText(C01B, "Pisaría a B", 2).getResponse().getStatus()).isEqualTo(409);
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(3);
  }

  @Test
  void treeReadBeforeCommitKeepsTheConfirmedVersion() throws Exception {
    long capProy = fixture.draftCapProy(C01A);
    String original = treeText("C01A");
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    transaction.executeWithoutResult(
        status -> {
          // Las mismas escrituras que la edición de texto: copia privada del año y UPDATE.
          writes.updateQuestionText(
              writes.copyQuestionForYear(capProy, "EDIN", "2027"), "Sin confirmar");
          RuleSet rules = rulesFiles.load("EDIN", 2027);
          rulesFiles.save(rules, 1, "adm.cuest", "editar-texto", List.of("C01A"));
          // Otra petición lee el árbol antes del COMMIT: ve la versión y las filas confirmadas.
          AdminQuestionnaireView before = readTreeFromAnotherThread();
          assertThat(before.version()).isEqualTo(1);
          assertThat(textOf(before, "C01A")).isEqualTo(original);
        });

    AdminQuestionnaireView after = readTreeFromAnotherThread();
    assertThat(after.version()).isEqualTo(2);
    assertThat(textOf(after, "C01A")).isEqualTo("Sin confirmar");
  }

  private String treeText(String columnCode) {
    return textOf(readTreeFromAnotherThread(), columnCode);
  }

  private AdminQuestionnaireView readTreeFromAnotherThread() {
    try {
      return executor.submit(() -> view.view("EDIN", 2027)).get(30, TimeUnit.SECONDS);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String textOf(AdminQuestionnaireView tree, String columnCode) {
    return tree.pages().stream()
        .map(AdminPage::entries)
        .flatMap(List::stream)
        .filter(entry -> columnCode.equals(entry.columnCode()))
        .map(AdminEntry::text)
        .findFirst()
        .orElseThrow();
  }

  private static String textOf(JsonNode page, String columnCode) {
    for (JsonNode entry : page.get("entries")) {
      if (columnCode.equals(entry.path("columnCode").asString(null))) {
        return entry.get("text").asString();
      }
    }
    throw new AssertionError("La página no tiene " + columnCode);
  }

  private JsonNode body(MvcResult result) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return jsonMapper.readTree(result.getResponse().getContentAsString());
  }

  private MvcResult editText(long capProy2026, String text, int version) throws Exception {
    return mockMvc
        .perform(
            put(DRAFT + "/entries/" + fixture.draftCapProy(capProy2026) + "/text")
                .with(realCsrf(mockMvc))
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("{\"text\":\"" + text + "\",\"version\":" + version + "}"))
        .andReturn();
  }

  private Cookie admin() {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(
            SessionPrincipal.administrator(
                "adm.cuest", EnumSet.of(Role.ADMIN_CUESTIONARIO), null)));
  }
}
