package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.rules.Condition;
import co.gov.dane.edi.rules.NewColumn;
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
import java.nio.file.Files;
import java.nio.file.Path;
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

@ExtendWith(OutputCaptureExtension.class)
class AddDeactivateQuestionIT extends OracleIntegrationTest {

  private static final String DRAFT = "/api/v1/admin/questionnaire/EDIN/2027";
  private static final String PUBLISHED = "/api/v1/admin/questionnaire/EDIN/2026";
  private static final String CHAPTER_C_TABLE = "EDIN_20_CAP_C_EXP_PERS";
  private static final long C01A = 301;
  private static final long C13 = 303;
  private static final long STATEMENT_ID = DraftYearFixture.OFFSET + 20001;

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RulesFileRepository rulesFiles;
  @Autowired private SurveyProperties survey;
  @Autowired private YearCopyService yearCopy;
  @Autowired private QuestionnaireService questionnaire;
  @Autowired private AnswerColumnRepository answerColumns;
  @Autowired private tools.jackson.databind.json.JsonMapper jsonMapper;

  private DraftYearFixture fixture;

  @BeforeEach
  void installDraftYear() {
    fixture = new DraftYearFixture(jdbc, rulesFiles, survey, yearCopy);
    fixture.install();
    questionnaire.evict();
  }

  @AfterEach
  void removeDraftYear() {
    for (String column : List.of("C14", "C15")) {
      dropColumnIfExists(column);
    }
    fixture.remove();
    questionnaire.evict();
  }

  @Test
  void addedQuestionWithoutColumnIsPendingAndHasScript(CapturedOutput output) throws Exception {
    List<Integer> ordersBefore = ordersOfGroups(201, 202);
    List<QuestionnaireEntry> c1Before =
        questionnaire.pageEntries(SurveyPage.find("C1").orElseThrow(), "EDIN", 2026);

    add(
            "C1",
            newQuestion(
                "C30A",
                "C30A.",
                "Pregunta <b>nueva</b>",
                "SINGLE_CHOICE",
                20,
                fixture.draftCapProy(C01A)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.page.entries[0].columnCode").value("C01A"))
        .andExpect(jsonPath("$.page.entries[1].columnCode").value("C30A"))
        .andExpect(jsonPath("$.page.entries[1].columnStatus").value("PENDING_COLUMN"))
        .andExpect(jsonPath("$.page.entries[1].inputType").value("SINGLE_CHOICE"))
        .andExpect(jsonPath("$.page.entries[1].optionGroup").value(20))
        .andExpect(jsonPath("$.page.entries[1].text").value("Pregunta <b>nueva</b>"))
        .andExpect(jsonPath("$.page.entries[2].columnCode").value("C01B"));

    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT P.PREGUNTA FROM FRM_PREGUNTA P"
                    + " JOIN FRM_PRE_GRUPO G ON G.COD_PRE = P.COD_PRE"
                    + " JOIN FRM_CAP_PROY C ON C.COD_PRE_GRUPO = G.COD_PRE_GRUPO"
                    + " WHERE C.PROY = 'EDIN' AND C.ANIO = '2027' AND C.NUMERAL = 'C30A.'",
                String.class))
        .isEqualTo("Pregunta &lt;b&gt;nueva&lt;/b&gt;");
    // 2026 no cambia: las preguntas compartidas se copiaron antes de correr el orden.
    assertThat(ordersOfGroups(201, 202)).isEqualTo(ordersBefore);
    assertThat(questionnaire.pageEntries(SurveyPage.find("C1").orElseThrow(), "EDIN", 2026))
        .isEqualTo(c1Before);
    // La pregunta pendiente no se sirve al encuestado.
    assertThat(
            questionnaire.pageEntries(SurveyPage.find("C1").orElseThrow(), "EDIN", 2027).stream()
                .map(entry -> entry.answer().columnCode()))
        .containsExactly("C01A", "C01B");
    assertThat(output.getAll())
        .contains("operacion=agregar-pregunta", "anio=2027", "codigos=[C30A]")
        .doesNotContain("Pregunta <b>nueva");

    long added = capProyOf("C30A.");
    script(added, "?columnType=VARCHAR2&columnLength=2")
        .andExpect(status().isOk())
        .andExpect(content().string("ALTER TABLE " + CHAPTER_C_TABLE + " ADD (C30A VARCHAR2(2));"));
    // Sin parámetros usa la definición guardada al agregar la pregunta.
    script(added, "")
        .andExpect(status().isOk())
        .andExpect(content().string("ALTER TABLE " + CHAPTER_C_TABLE + " ADD (C30A VARCHAR2(1));"));
    script(added, "?columnType=VARCHAR2&columnLength=4001")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.columnLength").exists());
    script(added, "?columnType=CLOB&columnLength=4")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.columnType").exists());
  }

  @Test
  void pendingQuestionBecomesActiveWhenTheColumnIsCreatedAndTheTreeIsReloaded() throws Exception {
    add("C2", newQuestion("C14", "C14.", "Pendiente", "TEXT", null, fixture.draftCapProy(C13)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.page.entries[1].columnCode").value("C14"))
        .andExpect(jsonPath("$.page.entries[1].columnStatus").value("PENDING_COLUMN"));
    long added = capProyOf("C14.");
    assertThat(treeStatusOf("C14")).isEqualTo("PENDING_COLUMN");
    script(added, "").andExpect(content().string(containsText("(C14 VARCHAR2(4000));")));

    jdbc.execute("ALTER TABLE " + CHAPTER_C_TABLE + " ADD (C14 VARCHAR2(10))");

    // Sin evictar nada a mano: el árbol de un borrador relee el diccionario de Oracle.
    assertThat(treeStatusOf("C14")).isEqualTo("ACTIVE");
    script(added, "").andExpect(status().isNotFound());
  }

  @Test
  void addedQuestionWithExistingColumnIsActive() throws Exception {
    jdbc.execute("ALTER TABLE " + CHAPTER_C_TABLE + " ADD (C15 VARCHAR2(1))");
    answerColumns.evict(CHAPTER_C_TABLE);

    add("C1", newQuestion("C15", "C15.", "Con columna", "CHECKBOX", 20, null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.page.entries[0].columnCode").value("C15"))
        .andExpect(jsonPath("$.page.entries[0].columnStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.page.entries[0].inputType").value("CHECKBOX"))
        .andExpect(jsonPath("$.page.entries[1].columnCode").value("C01A"))
        .andExpect(jsonPath("$.page.entries[2].columnCode").value("C01B"));

    // Quedó de primera en su bloque: ORDEN 1, y las demás corrieron una posición.
    assertThat(orderOfCapProy(capProyOf("C15."))).isEqualTo(1);
    assertThat(orderOfCapProy(fixture.draftCapProy(C01A))).isEqualTo(2);
    assertThat(orderOfCapProy(fixture.draftCapProy(302))).isEqualTo(3);
    // La pregunta activa sí la ve el encuestado de 2027.
    assertThat(
            questionnaire.pageEntries(SurveyPage.find("C1").orElseThrow(), "EDIN", 2027).stream()
                .map(entry -> entry.answer().columnCode()))
        .containsExactly("C15", "C01A", "C01B");
    assertThat(
            jdbc.queryForObject(
                "SELECT COD_OBJ FROM FRM_OBJ_PRE WHERE COD_CAP_PROY = ?",
                Integer.class,
                capProyOf("C15.")))
        .isEqualTo(2);
    script(capProyOf("C15."), "").andExpect(status().isNotFound());
  }

  @Test
  void addRejectsDuplicateCodeOrNumeralOrBadGroup() throws Exception {
    insertStatement("C20.");
    long questionsBefore = count("FRM_PREGUNTA");
    long capProyBefore = count("FRM_CAP_PROY");

    // Código repetido en la página y en otra página del capítulo.
    rejected("C1", newQuestion("C01B", "C01B.", "x", "TEXT", null, null), "columnCode");
    rejected("C1", newQuestion("C13", "C13.", "x", "TEXT", null, null), "columnCode");
    // El numeral ya existe en la página (aquí lo ocupa un enunciado sin columna).
    rejected("C1", newQuestion("C20", "C20.", "x", "TEXT", null, null), "numeral");
    // Código o numeral con formato inválido o que no coinciden.
    rejected("C1", newQuestion("c30a", "C30A.", "x", "TEXT", null, null), "columnCode");
    rejected("C1", newQuestion("C30A; DROP", "C30A.", "x", "TEXT", null, null), "columnCode");
    rejected("C1", newQuestion("C30A", "C31A.", "x", "TEXT", null, null), "numeral");
    // Grupos de opciones.
    rejected("C1", newQuestion("C30A", "C30A.", "x", "SINGLE_CHOICE", 9999, null), "optionGroup");
    rejected("C1", newQuestion("C30A", "C30A.", "x", "SINGLE_CHOICE", null, null), "optionGroup");
    rejected("C1", newQuestion("C30A", "C30A.", "x", "TEXT", 20, null), "optionGroup");
    // Tipo y longitud de columna.
    rejected("C1", withColumn("C30A", "VARCHAR2", 0), "columnLength");
    rejected("C1", withColumn("C30A", "VARCHAR2", 4001), "columnLength");
    rejected("C1", withColumn("C30A", "NUMBER", 39), "columnLength");
    rejected("C1", withColumn("C30A", "CLOB", 5), "columnType");
    rejected("C1", withColumn("C30A", "VARCHAR2", null), "columnLength");
    // Texto vacío tras quitar caracteres de control.
    rejected("C1", newQuestion("C30A", "C30A.", "\\u0007", "TEXT", null, null), "text");
    // Pregunta de referencia en otra página.
    rejected(
        "C1",
        newQuestion("C30A", "C30A.", "x", "TEXT", null, fixture.draftCapProy(C13)),
        "afterCapProyId");

    add("C1", newQuestion("C30A", "C30A.", "x", "TEXT", null, 301L))
        .andExpect(status().isNotFound());
    add("Z9", newQuestion("C30A", "C30A.", "x", "TEXT", null, null))
        .andExpect(status().isNotFound());
    add("C1", newQuestion("C30A", "C30A.", "x", "TEXT", null, null).replace(",\"version\":1", ""))
        .andExpect(status().isBadRequest());

    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
    assertThat(count("FRM_PREGUNTA")).isEqualTo(questionsBefore);
    assertThat(count("FRM_CAP_PROY")).isEqualTo(capProyBefore);
  }

  @Test
  void addToPublishedYearOrWithStaleVersionIsRejected() throws Exception {
    mockMvc
        .perform(
            post(PUBLISHED + "/pages/C1/entries")
                .with(realCsrf(mockMvc))
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(newQuestion("C30A", "C30A.", "x", "TEXT", null, null)))
        .andExpect(status().isConflict());
    add("C1", newQuestion("C30A", "C30A.", "x", "TEXT", null, null).replace(":1}", ":7}"))
        .andExpect(status().isConflict());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void deactivateRemovesOnlyThisYearsPeriodRow(CapturedOutput output) throws Exception {
    long capProy = fixture.draftCapProy(C13);
    // La misma fila de FRM_CAP_PROY también se usa en otro año: ese periodo debe sobrevivir.
    jdbc.update(
        "INSERT INTO FRM_CAP_PERIODO (ID_PERIODO, ANIO, PROY, COD_CAP_PROY, NUMERAL)"
            + " VALUES (?, 2025, 'EDIN', ?, 'C13*.')",
        DraftYearFixture.OFFSET + 30001,
        capProy);
    long c2Before =
        questionnaire.pageEntries(SurveyPage.find("C2").orElseThrow(), "EDIN", 2026).size();

    mockMvc
        .perform(
            delete(DRAFT + "/entries/" + capProy)
                .param("version", "1")
                .with(realCsrf(mockMvc))
                .cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.page.pageCode").value("C2"))
        .andExpect(jsonPath("$.page.entries.length()").value(0));

    assertThat(periodRows(capProy, 2027)).isZero();
    assertThat(periodRows(capProy, 2025)).isEqualTo(1);
    assertThat(periodRows(C13, 2026)).isEqualTo(1);
    assertThat(count("FRM_CAP_PROY WHERE COD_CAP_PROY = " + capProy)).isEqualTo(1);
    assertThat(questionnaire.pageEntries(SurveyPage.find("C2").orElseThrow(), "EDIN", 2026))
        .hasSize((int) c2Before);
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(2);
    assertThat(output.getAll()).contains("operacion=desactivar-pregunta", "codigos=[C13]");
  }

  @Test
  void deactivateRejectedWhenRulesReferenceIt() throws Exception {
    // El salto heredado de FRM_SALTO (2026) quedó en el archivo de 2027 al crear el año.
    mockMvc
        .perform(deactivate(fixture.draftCapProy(C01A), 1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors.rules[0]").value("legacy-C01A"));

    RuleSet draft = rulesFiles.load("EDIN", 2027);
    SkipRule rule =
        new SkipRule(
            "regla-h01",
            "Si H01 es No se deshabilita C13",
            new Condition("H01", "eq", "2", null, null, null, null, null),
            new SkipAction("deshabilitar", List.of("C13")));
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
    mockMvc
        .perform(deactivate(fixture.draftCapProy(C13), 2))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors.rules", hasItem("regla-h01")));

    assertThat(periodRows(fixture.draftCapProy(C01A), 2027)).isEqualTo(1);
    assertThat(periodRows(fixture.draftCapProy(C13), 2027)).isEqualTo(1);
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(2);
  }

  @Test
  void deactivateRespectsYearOwnershipStateAndVersion() throws Exception {
    // Una fila de 2026 no se desactiva por la ruta del borrador.
    mockMvc.perform(deactivate(C13, 1)).andExpect(status().isNotFound());
    mockMvc.perform(deactivate(999999, 1)).andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete(PUBLISHED + "/entries/" + C13)
                .param("version", "1")
                .with(realCsrf(mockMvc))
                .cookie(admin()))
        .andExpect(status().isConflict());
    mockMvc.perform(deactivate(fixture.draftCapProy(C13), 9)).andExpect(status().isConflict());
    assertThat(periodRows(C13, 2026)).isEqualTo(1);
    assertThat(periodRows(fixture.draftCapProy(C13), 2027)).isEqualTo(1);
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  @Test
  void storedColumnDefinitionDrivesTheScriptAndDeactivateRemovesIt() throws Exception {
    String body =
        newQuestion("C30A", "C30A.", "Cantidad", "TEXT", null, null)
            .replace(
                ",\"version\":1", ",\"columnType\":\"NUMBER\",\"columnLength\":5,\"version\":1");
    add("C1", body).andExpect(status().isCreated());
    assertThat(rulesFiles.load("EDIN", 2027).newColumns().get("C30A"))
        .isEqualTo(new NewColumn("NUMBER", 5));
    long added = capProyOf("C30A.");
    script(added, "")
        .andExpect(content().string("ALTER TABLE " + CHAPTER_C_TABLE + " ADD (C30A NUMBER(5));"));
    script(added, "?columnType=VARCHAR2&columnLength=9")
        .andExpect(content().string("ALTER TABLE " + CHAPTER_C_TABLE + " ADD (C30A VARCHAR2(9));"));

    mockMvc.perform(deactivate(added, 2)).andExpect(status().isOk());

    assertThat(rulesFiles.load("EDIN", 2027).newColumns()).isEmpty();
    assertThat(jsonMapper.writeValueAsString(rulesFiles.load("EDIN", 2026)))
        .doesNotContain("columnasNuevas");
    assertThat(Files.readString(Path.of(survey.rulesDir()).resolve("EDIN-2027.json")))
        .doesNotContain("columnasNuevas");
  }

  @Test
  void derivedDefinitionIsStoredAndKeptAfterTheColumnExists() throws Exception {
    add("C1", newQuestion("C14", "C14.", "Texto libre", "TEXT", null, null))
        .andExpect(status().isCreated());
    assertThat(rulesFiles.load("EDIN", 2027).newColumns().get("C14"))
        .isEqualTo(new NewColumn("VARCHAR2", 4000));
    jdbc.execute("ALTER TABLE " + CHAPTER_C_TABLE + " ADD (C14 VARCHAR2(4000))");
    answerColumns.evict(CHAPTER_C_TABLE);
    assertThat(treeStatusOf("C14")).isEqualTo("ACTIVE");
    assertThat(rulesFiles.load("EDIN", 2027).newColumns()).containsKey("C14");
  }

  @Test
  void activeTextQuestionRendersForTheRespondentOfThatYear() throws Exception {
    jdbc.execute("ALTER TABLE " + CHAPTER_C_TABLE + " ADD (C15 VARCHAR2(50))");
    answerColumns.evict(CHAPTER_C_TABLE);
    add("C1", newQuestion("C15", "C15.", "Texto activo", "TEXT", null, null))
        .andExpect(status().isCreated());
    // Convención heredada: el GRUPO más usado por las preguntas de texto del año (11).
    assertThat(
            jdbc.queryForObject(
                "SELECT GRUPO FROM FRM_OBJ_PRE WHERE COD_CAP_PROY = ?",
                Integer.class,
                capProyOf("C15.")))
        .isEqualTo(11);
    List<QuestionnaireEntry> entries =
        questionnaire.pageEntries(SurveyPage.find("C1").orElseThrow(), "EDIN", 2027);
    assertThat(entries)
        .anySatisfy(
            entry -> {
              assertThat(entry.answer().columnCode()).isEqualTo("C15");
              assertThat(entry.answer().inputType()).isEqualTo(InputType.TEXT);
              assertThat(entry.text()).isEqualTo("Texto activo");
            });
  }

  @Test
  void textQuestionWithoutAnyTextPeerGetsNullGroup() throws Exception {
    jdbc.update(
        "DELETE FROM FRM_OBJ_PRE WHERE COD_OBJ = 5 AND COD_CAP_PROY IN"
            + " (SELECT COD_CAP_PROY FROM FRM_CAP_PROY WHERE PROY = 'EDIN' AND ANIO = '2027')");
    add("C1", newQuestion("C14", "C14.", "Sin pares", "TEXT", null, null))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM FRM_OBJ_PRE WHERE COD_CAP_PROY = ? AND GRUPO IS NULL",
                Long.class,
                capProyOf("C14.")))
        .isEqualTo(1);
  }

  @Test
  void columnScriptWithUnusableNumeralIsUnprocessableNotServerError() throws Exception {
    jdbc.update(
        "UPDATE FRM_CAP_PERIODO SET NUMERAL = 'x-1' WHERE COD_CAP_PROY = ? AND ANIO = 2027",
        fixture.draftCapProy(C01A));
    jdbc.update(
        "UPDATE FRM_CAP_PROY SET NUMERAL = 'x-1' WHERE COD_CAP_PROY = ?",
        fixture.draftCapProy(C01A));
    questionnaire.evict();
    script(fixture.draftCapProy(C01A), "")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.columnCode").exists());
  }

  @Test
  void csrfAndRoleAreRequired() throws Exception {
    mockMvc
        .perform(
            post(DRAFT + "/pages/C1/entries")
                .cookie(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(newQuestion("C30A", "C30A.", "x", "TEXT", null, null)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            delete(DRAFT + "/entries/" + fixture.draftCapProy(C13))
                .param("version", "1")
                .cookie(admin()))
        .andExpect(status().isForbidden());
    Cookie national =
        new Cookie(
            SessionTokenService.SESSION_COOKIE_NAME,
            tokens.issueToken(
                SessionPrincipal.administrator("adm.nac", EnumSet.of(Role.ADMIN_NACIONAL), null)));
    mockMvc
        .perform(
            post(DRAFT + "/pages/C1/entries")
                .with(realCsrf(mockMvc))
                .cookie(national)
                .contentType(MediaType.APPLICATION_JSON)
                .content(newQuestion("C30A", "C30A.", "x", "TEXT", null, null)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get(DRAFT + "/entries/" + fixture.draftCapProy(C13) + "/column-script")
                .cookie(national))
        .andExpect(status().isForbidden());
    assertThat(rulesFiles.load("EDIN", 2027).version()).isEqualTo(1);
  }

  private ResultActions add(String page, String body) throws Exception {
    return mockMvc.perform(
        post(DRAFT + "/pages/" + page + "/entries")
            .with(realCsrf(mockMvc))
            .cookie(admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private void rejected(String page, String body, String field) throws Exception {
    add(page, body)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors." + field).exists());
  }

  private ResultActions script(long capProyId, String query) throws Exception {
    return mockMvc.perform(
        get(DRAFT + "/entries/" + capProyId + "/column-script" + query).cookie(admin()));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder deactivate(
      long capProyId, int version) throws Exception {
    return delete(DRAFT + "/entries/" + capProyId)
        .param("version", String.valueOf(version))
        .with(realCsrf(mockMvc))
        .cookie(admin());
  }

  private static org.hamcrest.Matcher<String> containsText(String fragment) {
    return org.hamcrest.Matchers.containsString(fragment);
  }

  private static String newQuestion(
      String column, String numeral, String text, String type, Integer group, Long after) {
    return "{\"columnCode\":\""
        + column
        + "\",\"numeral\":\""
        + numeral
        + "\",\"text\":\""
        + text
        + "\",\"inputType\":\""
        + type
        + "\""
        + (group == null ? "" : ",\"optionGroup\":" + group)
        + (after == null ? "" : ",\"afterCapProyId\":" + after)
        + ",\"version\":1}";
  }

  private static String withColumn(String column, String type, Integer length) {
    return "{\"columnCode\":\""
        + column
        + "\",\"numeral\":\""
        + column
        + ".\",\"text\":\"x\",\"inputType\":\"TEXT\",\"columnType\":\""
        + type
        + "\""
        + (length == null ? "" : ",\"columnLength\":" + length)
        + ",\"version\":1}";
  }

  /** Estado de la columna de una pregunta en el árbol completo del borrador. */
  private String treeStatusOf(String column) throws Exception {
    String json =
        mockMvc
            .perform(get(DRAFT).cookie(admin()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> statuses =
        com.jayway.jsonpath.JsonPath.read(
            json, "$.pages[*].entries[?(@.columnCode == '" + column + "')].columnStatus");
    return statuses.get(0);
  }

  private long capProyOf(String numeral) {
    return jdbc.queryForObject(
        "SELECT COD_CAP_PROY FROM FRM_CAP_PROY WHERE PROY = 'EDIN' AND ANIO = '2027'"
            + " AND NUMERAL = ?",
        Long.class,
        numeral);
  }

  private int orderOfCapProy(long capProyId) {
    return jdbc.queryForObject(
        "SELECT G.ORDEN FROM FRM_PRE_GRUPO G JOIN FRM_CAP_PROY C"
            + " ON C.COD_PRE_GRUPO = G.COD_PRE_GRUPO WHERE C.COD_CAP_PROY = ?",
        Integer.class,
        capProyId);
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

  private long periodRows(long capProyId, int year) {
    return count("FRM_CAP_PERIODO WHERE COD_CAP_PROY = " + capProyId + " AND ANIO = " + year);
  }

  private long count(String fromClause) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause, Long.class);
  }

  /** Enunciado (sin columna) de 2027 en C1 con el numeral indicado. */
  private void insertStatement(String numeral) {
    jdbc.update(
        "INSERT INTO FRM_PREGUNTA (COD_PRE, PREGUNTA, TIPO) VALUES (?, 'Enunciado ficticio', 'P')",
        STATEMENT_ID);
    jdbc.update(
        "INSERT INTO FRM_PRE_GRUPO (COD_PRE_GRUPO, GRUPO, ORDEN, COD_PRE) VALUES (?, 3, 1, ?)",
        STATEMENT_ID,
        STATEMENT_ID);
    jdbc.update(
        "INSERT INTO FRM_CAP_PROY (COD_CAP_PROY, CAPITULO, NUMERAL, COD_PRE_GRUPO, PROY, ANIO)"
            + " VALUES (?, 'C1', ?, ?, 'EDIN', '2027')",
        STATEMENT_ID,
        numeral,
        STATEMENT_ID);
    jdbc.update(
        "INSERT INTO FRM_CAP_PERIODO (ID_PERIODO, ANIO, PROY, COD_CAP_PROY, NUMERAL)"
            + " VALUES (?, 2027, 'EDIN', ?, ?)",
        STATEMENT_ID,
        STATEMENT_ID,
        numeral);
    questionnaire.evict();
  }

  private void dropColumnIfExists(String column) {
    Long found =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
            Long.class,
            CHAPTER_C_TABLE,
            column);
    if (found != null && found > 0) {
      jdbc.execute("ALTER TABLE " + CHAPTER_C_TABLE + " DROP COLUMN " + column);
    }
    answerColumns.evict(CHAPTER_C_TABLE);
  }

  private Cookie admin() {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(
            SessionPrincipal.administrator(
                "adm.cuest", EnumSet.of(Role.ADMIN_CUESTIONARIO), null)));
  }
}
