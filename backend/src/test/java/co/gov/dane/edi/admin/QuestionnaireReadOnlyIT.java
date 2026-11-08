package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Producción: el cuestionario es de solo lectura. Toda operación de la administración que escribe
 * responde 403 sin tocar FRM_* ni el archivo de reglas; las lecturas y la importación del paquete
 * de promoción siguen funcionando. El año 2027 publicado se arma con los servicios, por debajo de
 * la compuerta web, como lo dejaría un paquete ya importado.
 */
@TestPropertySource(properties = "app.admin.questionnaire-writes-enabled=false")
class QuestionnaireReadOnlyIT extends OracleIntegrationTest {

  private static final String YEAR = "/api/v1/admin/questionnaire/EDIN/2027";
  private static final String TITLE = "Cuestionario de solo lectura";
  private static final String DETAIL =
      "En este ambiente el cuestionario es de solo lectura: los cambios se hacen en QA y llegan con"
          + " un paquete de promoción.";
  private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}]+)}");
  private static final Map<String, String> SAMPLE_VALUES =
      Map.of("encuesta", "EDIN", "anio", "2027", "pageCode", "C1", "column", "C01A");

  /** Las escrituras que existen hoy; si aparece otra, esta lista obliga a revisarla. */
  private static final Set<String> KNOWN_WRITES =
      Set.of(
          "QuestionnaireAdminController.editQuestionText",
          "QuestionnaireAdminController.editHeader",
          "QuestionnaireAdminController.editHelp",
          "QuestionnaireAdminController.reorderPage",
          "QuestionnaireAdminController.addQuestion",
          "QuestionnaireAdminController.deactivateQuestion",
          "YearAdminController.createYear",
          "YearAdminController.saveTables",
          "RulesAdminController.saveSkip",
          "RulesAdminController.deleteSkip",
          "RulesAdminController.saveValidation",
          "RulesAdminController.deleteValidation",
          "RulesAdminController.saveCase",
          "RulesAdminController.deleteCase",
          "RulesAdminController.importRules",
          "PublicationAdminController.publish",
          "HistoryAdminController.restore");

  /**
   * Escrituras que producción sí permite (@AllowedInReadOnlyQuestionnaire): el paquete de
   * promoción, los cortes de cobertura, las novedades y las claves, que no tocan el cuestionario.
   * Una nueva exige revisarla aquí.
   */
  private static final Set<String> ALLOWED_WRITES =
      Set.of(
          "PackageAdminController.importPackage",
          "ReportController.createCut",
          "NoveltyController.people",
          "NoveltyController.assign",
          "NoveltyController.remove",
          "SampleKeysController.generate");

  /** Su 403 documentado es de alcance o de rol, no de la compuerta de solo lectura. */
  private static final Set<String> ALLOWED_WRITE_OPERATION_IDS =
      Set.of(
          "packageImport",
          "reportCutCreate",
          "noveltyPeople",
          "noveltyAssign",
          "noveltyRemove",
          "keysGenerate");

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private SurveyProperties survey;
  @Autowired private YearCopyService yearCopy;
  @Autowired private QuestionnaireService questionnaire;
  @Autowired private AnswerColumnRepository answerColumns;
  @Autowired private QuestionFactsService factsService;
  @Autowired private PublicationService publication;
  @Autowired private FrmSnapshotRepository snapshots;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  private PublishedYearFixture fixture;

  @BeforeEach
  void installPublishedYear() {
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
    fixture.installPublished("fixture");
    // El año activo se siembra en la primera lectura; se hace antes de tomar la fotografía.
    rulesFiles.load("EDIN", 2026);
  }

  @AfterEach
  void removeYear() {
    fixture.remove();
  }

  @Test
  void environmentReportsReadOnly() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/environment").cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.questionnaireWritesEnabled").value(false));
  }

  @Test
  void everyQuestionnaireWriteIs403AndChangesNothing() throws Exception {
    Map<String, Object> before = frmAndRulesState();
    Set<String> blocked = new TreeSet<>();

    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
        handlerMapping.getHandlerMethods().entrySet()) {
      HandlerMethod handler = entry.getValue();
      for (String pattern : entry.getKey().getPatternValues()) {
        if (!isWritableApi(pattern)) {
          continue;
        }
        for (RequestMethod method : methodsOf(entry.getKey())) {
          if (method == RequestMethod.GET) {
            continue;
          }
          String name = handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName();
          if (handler.hasMethodAnnotation(AllowedInReadOnlyQuestionnaire.class)) {
            assertThat(ALLOWED_WRITES).contains(name);
            continue;
          }
          blocked.add(name);
          mockMvc
              .perform(
                  request(HttpMethod.valueOf(method.name()), concrete(pattern))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"version\":1}")
                      .with(realCsrf(mockMvc))
                      .cookie(admin()))
              .andExpect(status().isForbidden())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
              .andExpect(jsonPath("$.title").value(TITLE))
              .andExpect(jsonPath("$.detail").value(DETAIL));
        }
      }
    }

    assertThat(blocked).containsExactlyInAnyOrderElementsOf(KNOWN_WRITES);
    assertThat(frmAndRulesState()).isEqualTo(before);
  }

  /** El contrato documenta el 403 en exactamente las operaciones que la compuerta rechaza. */
  @Test
  void contractDocuments403OnBlockedOperationsOnly() throws Exception {
    String json =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode paths = jsonMapper.readTree(json).get("paths");
    Set<String> documented = new TreeSet<>();
    Set<String> writes = new TreeSet<>();
    for (String path : paths.propertyNames()) {
      if (!isWritableApi(path)) {
        continue;
      }
      for (String method : paths.get(path).propertyNames()) {
        JsonNode operation = paths.get(path).get(method);
        String id = operation.get("operationId").asString();
        if (ALLOWED_WRITE_OPERATION_IDS.contains(id)) {
          continue;
        }
        if (!"get".equals(method)) {
          writes.add(id);
        }
        if (operation.get("responses").has("403")) {
          documented.add(id);
        }
      }
    }
    assertThat(writes).hasSize(KNOWN_WRITES.size());
    assertThat(documented).isEqualTo(writes);
  }

  @Test
  void readsStillRespond() throws Exception {
    Map<String, Object> before = frmAndRulesState();

    for (String path :
        List.of(
            "/api/v1/admin/years/EDIN",
            YEAR,
            YEAR + "/rules",
            YEAR + "/rules/export",
            YEAR + "/simulator",
            YEAR + "/history",
            YEAR + "/publication")) {
      mockMvc.perform(get(path).cookie(admin())).andExpect(status().isOk());
    }
    mockMvc
        .perform(get(YEAR + "/package").param("tipo", "anio").cookie(admin()))
        .andExpect(status().isOk());

    assertThat(frmAndRulesState()).isEqualTo(before);
  }

  /** Producción recibe los cambios por aquí: revisar e importar el paquete sigue permitido. */
  @Test
  void packageImportStillWorksAndOnlyWritesTheRulesFile() throws Exception {
    byte[] zip = download();
    RuleSet published = rulesFiles.load("EDIN", 2027);
    FrmSnapshot frmBefore = snapshots.snapshot("EDIN", 2027);
    Files.delete(rulesFile());
    rulesFiles.evict();

    importPackage(zip, false)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alreadyImported").value(false));
    assertThat(Files.exists(rulesFile())).isFalse();

    importPackage(zip, true).andExpect(status().isOk());

    RuleSet imported = rulesFiles.load("EDIN", 2027);
    assertThat(imported.withLastChange(null)).isEqualTo(published.withLastChange(null));
    assertThat(imported.lastChange().operation()).isEqualTo("importar-paquete");
    assertThat(snapshots.snapshot("EDIN", 2027)).isEqualTo(frmBefore);
  }

  /**
   * Toda la API salvo la del encuestado y la de sesión: cualquier escritura que aparezca allí debe
   * responder 403 o estar exceptuada de forma explícita. Fuera de /api solo quedan rutas del
   * framework (/error) y de documentación (solo lectura).
   */
  private static boolean isWritableApi(String pattern) {
    return pattern.startsWith("/api/")
        && !pattern.startsWith("/api/v1/auth/")
        && !pattern.startsWith("/api/v1/survey/");
  }

  /** Un mapeo sin métodos acepta cualquiera: se prueba como POST para no saltarlo. */
  private static Set<RequestMethod> methodsOf(RequestMappingInfo info) {
    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
    return methods.isEmpty() ? Set.of(RequestMethod.POST) : methods;
  }

  private static String concrete(String pattern) {
    Matcher matcher = PATH_VARIABLE.matcher(pattern);
    StringBuilder path = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(path, SAMPLE_VALUES.getOrDefault(matcher.group(1), "1"));
    }
    matcher.appendTail(path);
    return path.toString();
  }

  /** FRM_* completas (todas las encuestas y años) y cada archivo del directorio de reglas. */
  private Map<String, Object> frmAndRulesState() throws IOException {
    Map<String, Object> state = new TreeMap<>();
    for (FrmTable table : FrmTable.values()) {
      state.put(
          table.tableName(),
          jdbc.queryForList(
              "SELECT * FROM " + table.tableName() + " ORDER BY " + table.idColumn()));
    }
    state.put("FRM 2027", snapshots.snapshot("EDIN", 2027));
    try (Stream<Path> files = Files.walk(Path.of(survey.rulesDir()))) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        state.put(file.toString(), List.of(Files.readString(file)));
      }
    }
    return state;
  }

  private byte[] download() throws Exception {
    return mockMvc
        .perform(get(YEAR + "/package").param("tipo", "anio").cookie(admin()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
  }

  private ResultActions importPackage(byte[] zip, boolean confirm) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("archivo", "paquete.zip", "application/zip", zip);
    return mockMvc.perform(
        multipart(YEAR + "/package/import")
            .file(file)
            .param("confirmar", String.valueOf(confirm))
            .with(realCsrf(mockMvc))
            .cookie(admin()));
  }

  private Path rulesFile() {
    return Path.of(survey.rulesDir()).resolve("EDIN-2027.json");
  }

  private Cookie admin() {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(
            SessionPrincipal.administrator(
                "adm.cuest", EnumSet.of(Role.ADMIN_CUESTIONARIO), null)));
  }
}
