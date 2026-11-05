package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.rules.NewColumn;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.TestCase;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import co.gov.dane.edi.support.PackageZips;
import co.gov.dane.edi.support.PublishedYearFixture;
import co.gov.dane.edi.support.SqlPlusRunner;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Generación del paquete de promoción de un año publicado (2027, publicado con el fixture). */
@ExtendWith(OutputCaptureExtension.class)
class PromotionPackageIT extends OracleIntegrationTest {

  private static final String YEAR = "/api/v1/admin/questionnaire/EDIN/2027";
  private static final String PACKAGE = YEAR + "/package";
  private static final List<String> ENTRIES =
      List.of("manifiesto.json", "reglas.json", "frm.sql", "columnas.sql");
  private static final String REASON = "Se corrige la regla según el comité técnico";
  private static final String HARMLESS_SKIP =
      """
      {"rule":{"id":"c01a-si-oculta-c01b","si":{"pregunta":"C01A","op":"eq","valor":"1"},
        "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},"version":%d,"motivo":"%s"}""";
  private static final String NEW_TABLE = "EDIN_27_CAP_C";
  private static final String H_TABLE = "EDIN_20_CAP_H_PREV_PRACT";

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
  @Autowired private AnswerTableResolver tables;
  @Autowired private FrmSnapshotRepository snapshots;
  @Autowired private FrmScriptWriter writer;

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
    try {
      jdbc.execute(
          "BEGIN EXECUTE IMMEDIATE 'DROP TABLE "
              + NEW_TABLE
              + "'; EXCEPTION WHEN OTHERS THEN NULL; END;");
    } finally {
      tables.evict();
      fixture.remove();
    }
  }

  @Test
  void yearPackageHasFourFilesWithMatchingSums(CapturedOutput output) throws Exception {
    fixture.installPublished("fixture");

    byte[] zip =
        send(get(PACKAGE).param("tipo", "anio"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename=\"EDIN-2027-v2-anio.zip\"")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    Map<String, byte[]> entries = PackageZips.read(zip);
    assertThat(entries.keySet()).containsExactlyElementsOf(ENTRIES);
    JsonNode manifest = jsonMapper.readTree(entries.get("manifiesto.json"));
    String characterSet =
        jdbc.queryForObject(
            "SELECT VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER = 'NLS_CHARACTERSET'",
            String.class);
    assertThat(manifest.get("encuesta").asString()).isEqualTo("EDIN");
    assertThat(manifest.get("anio").asInt()).isEqualTo(2027);
    assertThat(manifest.get("version").asInt()).isEqualTo(2);
    assertThat(manifest.get("tipo").asString()).isEqualTo("anio");
    assertThat(manifest.get("generadoPor").asString()).isEqualTo("admin:adm.cuest");
    assertThat(manifest.get("generado").asString()).isNotBlank();
    assertThat(manifest.get("juegoCaracteres").asString()).isEqualTo(characterSet);

    JsonNode files = manifest.get("archivos");
    assertThat(files.propertyNames())
        .containsExactlyInAnyOrder("reglas.json", "frm.sql", "columnas.sql");
    for (String name : List.of("reglas.json", "frm.sql", "columnas.sql")) {
      assertThat(files.get(name).asString()).isEqualTo(PackageZips.sha256(entries.get(name)));
    }

    FrmSnapshot snapshot = snapshots.snapshot("EDIN", 2027);
    JsonNode sums = manifest.get("tablas");
    assertThat(sums.propertyNames()).hasSize(FrmSnapshot.TABLES.size());
    for (FrmTable table : FrmSnapshot.TABLES) {
      List<Map<String, Object>> rows = snapshot.rows().get(table);
      List<String> hashes = new ArrayList<>();
      rows.forEach(row -> hashes.add(FrmSnapshot.rowHash(table, row)));
      JsonNode sum = sums.get(table.tableName());
      assertThat(sum.get("filas").asInt()).as(table.tableName()).isEqualTo(rows.size());
      assertThat(sum.get("suma").asString()).isEqualTo(FrmSnapshot.tableSum(hashes));
    }
    JsonNode required = manifest.get("columnasRequeridas");
    assertThat(required.get(H_TABLE).values().stream().map(JsonNode::asString))
        .contains("H01", "H02A");
    assertThat(required.get("EDIN_20_CAP_C_EXP_PERS").values().stream().map(JsonNode::asString))
        .contains("C01A", "C01B");

    byte[] rules = entries.get("reglas.json");
    assertThat(rules[0]).isEqualTo((byte) '{');
    assertThat(new String(rules, StandardCharsets.UTF_8)).doesNotContain("\r");
    assertThat(new String(entries.get("manifiesto.json"), StandardCharsets.UTF_8))
        .doesNotContain("\r")
        .startsWith("{");
    assertThat(jsonMapper.readValue(rules, RuleSet.class)).isEqualTo(rulesFiles.load("EDIN", 2027));

    assertThat(isAscii(entries.get("frm.sql"))).isTrue();
    assertThat(new String(entries.get("frm.sql"), StandardCharsets.US_ASCII))
        .isEqualTo(writer.write(snapshot, PackageKind.ANIO, characterSet))
        .contains("-- Juego de caracteres de origen: " + characterSet);
    assertThat(isAscii(entries.get("columnas.sql"))).isTrue();
    // Cada columna requerida lleva su bloque protegido, con la definición del diccionario de QA.
    String columnScript = new String(entries.get("columnas.sql"), StandardCharsets.US_ASCII);
    for (Map.Entry<String, JsonNode> table : required.properties()) {
      for (JsonNode column : table.getValue().values()) {
        assertThat(columnScript)
            .contains(
                "AND TABLE_NAME = '"
                    + table.getKey()
                    + "' AND COLUMN_NAME = '"
                    + column.asString()
                    + "';");
      }
    }
    assertThat(columnScript)
        .contains("ALTER TABLE " + H_TABLE + " ADD (H02A VARCHAR2(50))")
        .contains("ALTER TABLE EDIN_20_CAP_C_EXP_PERS ADD (C01A VARCHAR2(1))")
        .doesNotContain("CREATE TABLE");

    assertThat(output.getAll())
        .contains("operacion=generar-paquete")
        .contains("codigos=[anio, v2]");
  }

  @Test
  void correctionPackageNeedsALaterVersion() throws Exception {
    fixture.installPublished("fixture");

    send(get(PACKAGE).param("tipo", "correccion"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("No hay correcciones posteriores a la publicación."));

    send(put(YEAR + "/rules/skips/c01a-si-oculta-c01b"), HARMLESS_SKIP.formatted(2, REASON))
        .andExpect(status().isOk());

    byte[] zip =
        send(get(PACKAGE).param("tipo", "correccion"))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename=\"EDIN-2027-v3-correccion.zip\"")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    Map<String, byte[]> entries = PackageZips.read(zip);
    JsonNode manifest = jsonMapper.readTree(entries.get("manifiesto.json"));
    assertThat(manifest.get("tipo").asString()).isEqualTo("correccion");
    assertThat(manifest.get("version").asInt()).isEqualTo(3);
    assertThat(new String(entries.get("frm.sql"), StandardCharsets.US_ASCII))
        .contains("-- Paquete: correccion")
        .contains("UPDATE FRM_PREGUNTA SET PREGUNTA");
  }

  @Test
  void draftYearHasNoPackage() throws Exception {
    fixture.installDraft();

    for (String kind : List.of("anio", "correccion")) {
      send(get(PACKAGE).param("tipo", kind))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.detail").value("Solo se generan paquetes de un año publicado."));
    }
  }

  /**
   * Un año publicado que ya no pasa la lista (aquí un caso quedó por revisar) no genera paquete:
   * producción lo rechazaría al importarlo, y es mejor saberlo en QA.
   */
  @Test
  void publishedYearFailingTheChecklistHasNoPackage() throws Exception {
    fixture.installPublished("fixture");
    RuleSet published =
        rulesFiles.load(PublishedYearFixture.SURVEY_CODE, PublishedYearFixture.YEAR);
    assertThat(published.cases()).isNotEmpty();
    List<TestCase> cases = new ArrayList<>(published.cases());
    cases.set(0, cases.get(0).withNeedsReview(true));
    rulesFiles.save(
        published.withCases(cases), published.version(), "fixture", "editar-caso", List.of());

    for (String kind : List.of("anio", "correccion")) {
      send(get(PACKAGE).param("tipo", kind))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.title").value("Lista de publicación incompleta"))
          .andExpect(jsonPath("$.detail").value(PromotionPackageService.CHECKLIST_FAILS))
          .andExpect(jsonPath("$.checklist.ready").value(false))
          .andExpect(
              jsonPath("$.checklist.items[?(@.code == 'CASOS_EN_VERDE')].passed").value(false))
          .andExpect(
              jsonPath("$.checklist.items[?(@.code == 'CASOS_EN_VERDE')].elements[0]")
                  .value(cases.get(0).id()));
    }
  }

  @Test
  void unknownKindOrYearIsRejected() throws Exception {
    fixture.installPublished("fixture");

    send(get(PACKAGE).param("tipo", "todo")).andExpect(status().isBadRequest());
    send(get(PACKAGE)).andExpect(status().isBadRequest());
    send(get("/api/v1/admin/questionnaire/EDIN/2099/package").param("tipo", "anio"))
        .andExpect(status().isNotFound());
  }

  /**
   * columnas.sql con el SQL*Plus real: crea la tabla mapeada que falta y la columna nueva que
   * falta, y una segunda ejecución no hace nada.
   */
  @Test
  void columnScriptCreatesWhatIsMissingAndIsIdempotent() throws Exception {
    fixture.installPublished("fixture");
    jdbc.execute(
        "CREATE TABLE " + NEW_TABLE + " AS SELECT * FROM EDIN_20_CAP_C_EXP_PERS WHERE 1 = 0");
    RuleSet published = rulesFiles.load("EDIN", 2027);
    rulesFiles.save(
        published
            .withTables(Map.of("C", NEW_TABLE))
            .withNewColumns(Map.of("H02A", new NewColumn("VARCHAR2", 50))),
        published.version(),
        "fixture",
        "editar-tablas",
        List.of());
    tables.evict();
    questionnaire.evict();

    byte[] zip =
        send(get(PACKAGE).param("tipo", "anio"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    String script =
        new String(PackageZips.read(zip).get("columnas.sql"), StandardCharsets.US_ASCII);
    assertThat(script)
        .contains("CREATE TABLE " + NEW_TABLE + " AS SELECT * FROM EDIN_20_CAP_C_EXP_PERS")
        .contains("ALTER TABLE " + H_TABLE + " ADD (H02A VARCHAR2(50))");
    SqlPlusRunner sqlPlus = new SqlPlusRunner(ORACLE);

    SqlPlusRunner.Run unchanged = sqlPlus.run(script);
    assertThat(unchanged.exitCode()).as(unchanged.output()).isZero();

    jdbc.execute("DROP TABLE " + NEW_TABLE);
    jdbc.execute("ALTER TABLE " + H_TABLE + " DROP COLUMN H02A");
    SqlPlusRunner.Run created = sqlPlus.run(script);
    assertThat(created.exitCode()).as(created.output()).isZero();
    assertThat(created.output()).doesNotContain("ORA-").doesNotContain("SP2-");
    assertThat(columnExists(NEW_TABLE, "C_C")).isTrue();
    assertThat(columnExists(H_TABLE, "H02A")).isTrue();

    SqlPlusRunner.Run again = sqlPlus.run(script);
    assertThat(again.exitCode()).as(again.output()).isZero();
    answerColumns.evict(H_TABLE);
  }

  /**
   * Una columna que no está en {@code columnasNuevas} (H02A la crea el fixture a mano) también se
   * recrea, con el tipo que tiene en QA.
   */
  @Test
  void columnScriptRecreatesARequiredColumnWithItsQaType() throws Exception {
    fixture.installPublished("fixture");
    assertThat(rulesFiles.load("EDIN", 2027).newColumns()).doesNotContainKey("H02A");
    String before = columnType(H_TABLE, "H02A");
    byte[] zip =
        send(get(PACKAGE).param("tipo", "anio"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    String script =
        new String(PackageZips.read(zip).get("columnas.sql"), StandardCharsets.US_ASCII);

    jdbc.execute("ALTER TABLE " + H_TABLE + " DROP COLUMN H02A");
    SqlPlusRunner.Run created = new SqlPlusRunner(ORACLE).run(script);

    assertThat(created.exitCode()).as(created.output()).isZero();
    assertThat(columnType(H_TABLE, "H02A")).isEqualTo(before).isEqualTo("VARCHAR2/50/B");
    answerColumns.evict(H_TABLE);
  }

  private String columnType(String table, String column) {
    return jdbc.queryForObject(
        "SELECT DATA_TYPE || '/' || DATA_LENGTH || '/' || CHAR_USED FROM USER_TAB_COLUMNS"
            + " WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
        String.class,
        table,
        column);
  }

  private boolean columnExists(String table, String column) {
    Integer found =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
            Integer.class,
            table,
            column);
    return found != null && found == 1;
  }

  private static boolean isAscii(byte[] content) {
    for (byte value : content) {
      if (value < 0) {
        return false;
      }
    }
    return true;
  }

  private ResultActions send(MockHttpServletRequestBuilder builder) throws Exception {
    return send(builder, null);
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
