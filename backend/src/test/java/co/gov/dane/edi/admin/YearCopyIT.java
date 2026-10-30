package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.admin.YearCopyRepository.CopiedRange;
import co.gov.dane.edi.admin.dto.AdminEntry;
import co.gov.dane.edi.admin.dto.AdminHeader;
import co.gov.dane.edi.admin.dto.AdminPage;
import co.gov.dane.edi.admin.dto.AdminQuestionnaireView;
import co.gov.dane.edi.config.RulesSeedProperties;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.SaveLocks;
import co.gov.dane.edi.service.error.YearCreationFailedException;
import co.gov.dane.edi.support.DraftYearFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
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
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class YearCopyIT extends OracleIntegrationTest {

  private static final String YEARS = "/api/v1/admin/years/EDIN";
  private static final String DRAFT = "/api/v1/admin/questionnaire/EDIN/2027";
  private static final String NEW_B_TABLE = "EDIN_27_CAP_B_UBICA";
  private static final String NEW_C_TABLE = "EDIN_27_CAP_C_EXP_PERS";
  private static final String WITHOUT_KEY = "EDIN_27_SIN_CC";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private SurveyProperties survey;
  @Autowired private RulesSeedProperties seed;
  @Autowired private AdminAudit audit;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private QuestionnaireService questionnaire;
  @Autowired private QuestionnaireAdminService adminView;
  @Autowired private EffectiveRulesService effectiveRules;
  @Autowired private YearCatalogService years;
  @Autowired private YearCopyService yearCopy;
  @Autowired private YearCopyRepository copies;
  @Autowired private FrmIdAllocator ids;
  @Autowired private AnswerTableResolver tables;
  @Autowired private SaveLocks locks;
  @Autowired private PlatformTransactionManager transactionManager;

  private DraftYearFixture cleanup;

  @BeforeEach
  void captureBaseline() {
    cleanup = new DraftYearFixture(jdbc, rulesFiles, survey, yearCopy);
    cleanup.captureBaseline();
    questionnaire.evict();
  }

  @AfterEach
  void removeCopiedYears() {
    for (String table : List.of(NEW_B_TABLE, NEW_C_TABLE, WITHOUT_KEY)) {
      dropQuietly(table);
    }
    cleanup.remove();
    questionnaire.evict();
    effectiveRules.evict();
    tables.evict();
  }

  @Test
  void createYearCopiesRowsAndMaterializesLegacySkips(CapturedOutput output) throws Exception {
    createYear(2027, 2026)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.year").value(2027))
        .andExpect(jsonPath("$.state").value("borrador"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.active").value(false));

    AdminQuestionnaireView before = adminView.view("EDIN", 2026);
    AdminQuestionnaireView after = adminView.view("EDIN", 2027);
    assertThat(shapeOf(after)).isEqualTo(shapeOf(before)).isNotEmpty();
    assertThat(after.tables()).isEqualTo(before.tables());
    for (SurveyPage page : SurveyPage.ordered()) {
      assertThat(respondentShape(questionnaire.pageEntries(page, "EDIN", 2027)))
          .isEqualTo(respondentShape(questionnaire.pageEntries(page, "EDIN", 2026)));
    }
    // Cada pregunta de 2027 tiene su propio FRM_CAP_PROY (las ediciones no chocan con 2026).
    assertThat(count("FRM_CAP_PROY WHERE PROY = 'EDIN' AND ANIO = '2027'"))
        .isEqualTo(count("FRM_CAP_PERIODO WHERE PROY = 'EDIN' AND ANIO = 2026"));
    assertThat(
            count(
                "FRM_CAP_PERIODO P JOIN FRM_CAP_PROY C ON C.COD_CAP_PROY = P.COD_CAP_PROY"
                    + " WHERE P.ANIO = 2027 AND C.ANIO <> '2027'"))
        .isZero();
    // El encabezado de 2025 de la misma pregunta no se copia.
    assertThat(count("FRM_ENCABEZADO WHERE ANIO = '2027'"))
        .isEqualTo(
            count(
                "FRM_ENCABEZADO E JOIN FRM_CAP_PERIODO P ON P.COD_CAP_PROY = E.COD_CAP_PROY"
                    + " WHERE E.ANIO = '2026' AND P.PROY = 'EDIN' AND P.ANIO = 2026"));
    assertThat(
            count(
                "FRM_ENC_AYUDA A JOIN FRM_ENCABEZADO E ON E.COD_ENC = A.COD_ENC"
                    + " WHERE E.ANIO = '2027'"))
        .isEqualTo(1);
    assertThat(
            count(
                "FRM_SALTO S JOIN FRM_CAP_PROY C ON C.COD_CAP_PROY = S.COD_CAP_PROY"
                    + " WHERE C.ANIO = '2027'"))
        .isZero();

    RuleSet file = rulesFiles.find("EDIN", 2027).orElseThrow();
    assertThat(file.effectiveState()).isEqualTo(YearState.BORRADOR);
    assertThat(file.version()).isEqualTo(1);
    assertThat(file.skips()).extracting(SkipRule::id).contains("legacy-C01A");
    assertThat(file.newColumns()).isEmpty();
    assertThat(effectiveRules.effectiveRules("EDIN", 2027).skips())
        .extracting(SkipRule::id)
        .containsExactlyElementsOf(
            effectiveRules.effectiveRules("EDIN", 2026).skips().stream()
                .map(SkipRule::id)
                .toList());
    assertThat(output.getAll())
        .contains(
            "operacion=crear-anio", "encuesta=EDIN", "anio=2027", "codigos=[2027, desde-2026]");
  }

  @Test
  void createYearIsAllOrNothing() {
    Map<FrmTable, Long> before = counts();
    List<Long> periodsSeenBeforeFailure = new ArrayList<>();
    YearCopyRepository failing =
        new YearCopyRepository(jdbc, ids) {
          @Override
          void copyObjects(
              String project, int fromYear, Map<Long, Long> capProys, List<CopiedRange> copied) {
            periodsSeenBeforeFailure.add(count("FRM_CAP_PERIODO WHERE ANIO = 2027"));
            throw new IllegalStateException("Falla simulada a mitad de la copia");
          }
        };

    assertThatThrownBy(() -> serviceWith(failing, rulesFiles).createYear("EDIN", 2027, 2026, "t"))
        .isInstanceOf(IllegalStateException.class);

    assertThat(periodsSeenBeforeFailure).singleElement().satisfies(n -> assertThat(n).isPositive());
    assertThat(counts()).isEqualTo(before);
    assertThat(rulesFiles.find("EDIN", 2027)).isEmpty();
  }

  @Test
  void failedRulesFileWriteDeletesTheCopiedRows() {
    Map<FrmTable, Long> before = counts();
    RulesFileRepository failingFiles =
        new RulesFileRepository(jsonMapper, survey, seed, audit) {
          @Override
          public synchronized RuleSet save(
              RuleSet rules,
              int expectedVersion,
              String user,
              String operation,
              List<String> codes) {
            throw new UncheckedIOException(new java.io.IOException("Disco lleno simulado"));
          }
        };

    assertThatThrownBy(() -> serviceWith(copies, failingFiles).createYear("EDIN", 2027, 2026, "t"))
        .isInstanceOf(YearCreationFailedException.class)
        .hasMessage(
            "No fue posible guardar el archivo de reglas de 2027; el año no se creó. Intente de"
                + " nuevo.");

    assertThat(counts()).isEqualTo(before);
    assertThat(rulesFiles.find("EDIN", 2027)).isEmpty();
  }

  @Test
  void secondDraftOrExistingYearConflicts() throws Exception {
    createYear(2027, 2019).andExpect(status().isNotFound());
    createYear(1999, 2026).andExpect(status().isUnprocessableContent());
    mockMvc
        .perform(
            post(YEARS)
                .with(realCsrf(mockMvc))
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\":2027}"))
        .andExpect(status().isBadRequest());
    createYear(2027, 2026).andExpect(status().isCreated());
    Map<FrmTable, Long> afterFirst = counts();

    createYear(2028, 2026)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Ya hay un borrador abierto (2027)."));
    createYear(2027, 2026)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Ya existe un cuestionario para 2027."));
    createYear(2026, 2027)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Ya existe un cuestionario para 2026."));
    // Un año con filas en la base pero sin archivo también existe.
    jdbc.update(
        "INSERT INTO FRM_CAP_PERIODO (ID_PERIODO, ANIO, PROY, COD_CAP_PROY, NUMERAL)"
            + " VALUES (?, 2029, 'EDIN', 301, 'C01A.')",
        DraftYearFixture.OFFSET + 40001);
    createYear(2029, 2026)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Ya existe un cuestionario para 2029."));

    afterFirst.merge(FrmTable.CAP_PERIODO, 1L, Long::sum);
    assertThat(counts()).isEqualTo(afterFirst);
    assertThat(rulesFiles.find("EDIN", 2028)).isEmpty();
  }

  @Test
  void createYearRequiresCsrfAndRole() throws Exception {
    mockMvc
        .perform(
            post(YEARS)
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\":2027,\"fromYear\":2026}"))
        .andExpect(status().isForbidden());
    Cookie national =
        session(SessionPrincipal.administrator("adm.nac", EnumSet.of(Role.ADMIN_NACIONAL), null));
    mockMvc
        .perform(
            post(YEARS)
                .with(realCsrf(mockMvc))
                .cookie(national)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\":2027,\"fromYear\":2026}"))
        .andExpect(status().isForbidden());
    assertThat(rulesFiles.find("EDIN", 2027)).isEmpty();
  }

  @Test
  void tablesMappingSavedAndMissingTableReported(CapturedOutput output) throws Exception {
    createYear(2027, 2026).andExpect(status().isCreated());
    putTables("{\"B\":\"" + NEW_B_TABLE + "\",\"C\":\"EDIN_20_CAP_C_EXP_PERS\"}", 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.tables.B").value(NEW_B_TABLE))
        .andExpect(jsonPath("$.tables.C").value("EDIN_20_CAP_C_EXP_PERS"))
        .andExpect(jsonPath("$.missing.length()").value(1))
        .andExpect(jsonPath("$.missing[0]").value("B"));
    assertThat(rulesFiles.find("EDIN", 2027).orElseThrow().tables())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("B", NEW_B_TABLE, "C", "EDIN_20_CAP_C_EXP_PERS"));
    assertThat(output.getAll()).contains("operacion=editar-tablas", "codigos=[B, C]");

    jdbc.execute("CREATE TABLE " + WITHOUT_KEY + " (X VARCHAR2(1))");
    for (String unsafe : List.of("X; DROP", "edin_27_cap_b", WITHOUT_KEY)) {
      putTables("{\"B\":\"" + unsafe + "\"}", 2)
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.errors['tables.B']").exists())
          .andExpect(content().string(not(containsString(unsafe))));
    }
    putTables("{\"B\":\" \"}", 2)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors['tables.B']").value("Indique la tabla del capítulo."));
    putTables("{\"Z\":\"" + NEW_B_TABLE + "\"}", 2)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.tables").exists());
    putTables("{\"B\":\"" + NEW_B_TABLE + "\"}", 1)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("Otro usuario modificó el cuestionario. Recargue la página."));
    mockMvc
        .perform(
            put("/api/v1/admin/questionnaire/EDIN/2026/tables")
                .with(realCsrf(mockMvc))
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tables\":{},\"version\":1}"))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            put(DRAFT + "/tables")
                .with(realCsrf(mockMvc))
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}"))
        .andExpect(status().isBadRequest());
    assertThat(rulesFiles.find("EDIN", 2027).orElseThrow().version()).isEqualTo(2);
  }

  @Test
  void mappingChangeIsVisibleAfterSave() throws Exception {
    createYear(2027, 2026).andExpect(status().isCreated());
    assertThat(tables.tableFor(Chapter.C, "EDIN", 2027)).isEqualTo(Chapter.C.tableName());
    jdbc.execute(
        "CREATE TABLE " + NEW_C_TABLE + " AS SELECT * FROM EDIN_20_CAP_C_EXP_PERS WHERE 1 = 0");

    putTables("{\"C\":\"" + NEW_C_TABLE + "\"}", 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.missing.length()").value(0));

    assertThat(tables.tableFor(Chapter.C, "EDIN", 2027)).isEqualTo(NEW_C_TABLE);
    assertThat(adminView.view("EDIN", 2027).tables()).containsEntry("C", NEW_C_TABLE);
  }

  @Test
  void createScriptMatchesPreviousTable() throws Exception {
    createYear(2027, 2026).andExpect(status().isCreated());
    putTables("{\"B\":\"" + NEW_B_TABLE + "\"}", 1).andExpect(status().isOk());

    script("B")
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
        .andExpect(
            content()
                .string(
                    "CREATE TABLE "
                        + NEW_B_TABLE
                        + " AS SELECT * FROM EDIN_20_CAP_B_UBICA WHERE 1 = 0;"));
    // Sin tabla nueva asignada, con capítulo inexistente o con la tabla ya creada no hay script.
    script("C").andExpect(status().isNotFound());
    script("Z").andExpect(status().isNotFound());
    jdbc.execute(
        "CREATE TABLE " + NEW_B_TABLE + " AS SELECT * FROM EDIN_20_CAP_B_UBICA WHERE 1 = 0");
    script("B").andExpect(status().isNotFound());
  }

  private YearCopyService serviceWith(YearCopyRepository repository, RulesFileRepository files) {
    return new YearCopyService(
        years, files, repository, effectiveRules, questionnaire, tables, locks, transactionManager);
  }

  private ResultActions createYear(int year, int fromYear) throws Exception {
    return mockMvc.perform(
        post(YEARS)
            .with(realCsrf(mockMvc))
            .cookie(admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"year\":" + year + ",\"fromYear\":" + fromYear + "}"));
  }

  private ResultActions putTables(String mapping, int version) throws Exception {
    return mockMvc.perform(
        put(DRAFT + "/tables")
            .with(realCsrf(mockMvc))
            .cookie(admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tables\":" + mapping + ",\"version\":" + version + "}"));
  }

  private ResultActions script(String chapter) throws Exception {
    return mockMvc.perform(get(DRAFT + "/tables/" + chapter + "/create-script").cookie(admin()));
  }

  /** Lo que ve el editor sin los ids propios de cada año. */
  private static List<List<Object>> shapeOf(AdminQuestionnaireView view) {
    List<List<Object>> shape = new ArrayList<>();
    for (AdminPage page : view.pages()) {
      for (AdminEntry entry : page.entries()) {
        shape.add(
            List.of(
                page.pageCode(),
                String.valueOf(entry.numeral()),
                String.valueOf(entry.columnCode()),
                String.valueOf(entry.kind()),
                String.valueOf(entry.inputType()),
                String.valueOf(entry.rawText()),
                String.valueOf(entry.columnStatus()),
                String.valueOf(entry.optionGroup()),
                entry.headers().stream().map(AdminHeader::text).toList(),
                entry.help() == null ? "" : entry.help().text(),
                entry.referencedByRules()));
      }
    }
    return shape;
  }

  /** Lo que ve el encuestado: numerales, columnas, opciones y encabezados (sin ids). */
  private static List<List<Object>> respondentShape(List<QuestionnaireEntry> entries) {
    return entries.stream()
        .map(
            entry ->
                List.<Object>of(
                    String.valueOf(entry.numeral()),
                    String.valueOf(entry.kind()),
                    String.valueOf(entry.label()),
                    entry.headers(),
                    String.valueOf(entry.answer())))
        .toList();
  }

  private Map<FrmTable, Long> counts() {
    Map<FrmTable, Long> counts = new EnumMap<>(FrmTable.class);
    for (FrmTable table : FrmTable.values()) {
      counts.put(table, count(table.tableName()));
    }
    return counts;
  }

  private long count(String fromClause) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause, Long.class);
  }

  private void dropQuietly(String table) {
    try {
      jdbc.execute("DROP TABLE " + table + " PURGE");
    } catch (RuntimeException ignored) {
      // No existía: nada que limpiar.
    }
  }

  private Cookie admin() {
    return session(
        SessionPrincipal.administrator("adm.cuest", EnumSet.of(Role.ADMIN_CUESTIONARIO), null));
  }

  private Cookie session(SessionPrincipal principal) {
    return new Cookie(SessionTokenService.SESSION_COOKIE_NAME, tokens.issueToken(principal));
  }
}
