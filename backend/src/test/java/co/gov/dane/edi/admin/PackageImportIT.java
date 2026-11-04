package co.gov.dane.edi.admin;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.support.OracleIntegrationTest;
import co.gov.dane.edi.support.PackageZips;
import co.gov.dane.edi.support.PublishedYearFixture;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Importación del paquete en "producción". QA y producción son la misma base de pruebas: el paquete
 * se genera del 2027 publicado y, para simular un ambiente atrasado, la prueba deja el archivo de
 * reglas como estaba antes de la corrección.
 */
@ExtendWith(OutputCaptureExtension.class)
class PackageImportIT extends OracleIntegrationTest {

  private static final String YEAR = "/api/v1/admin/questionnaire/EDIN/2027";
  private static final String PACKAGE = YEAR + "/package";
  private static final String IMPORT = PACKAGE + "/import";
  private static final String REASON = "Se corrige la regla según el comité técnico";
  private static final String HARMLESS_SKIP =
      """
      {"rule":{"id":"c01a-si-oculta-c01b","si":{"pregunta":"C01A","op":"eq","valor":"1"},
        "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}},"version":%d,"motivo":"%s"}""";
  private static final String H_TABLE = "EDIN_20_CAP_H_PREV_PRACT";
  private static final String C_TABLE = "EDIN_20_CAP_C_EXP_PERS";
  private static final List<String> RESPONDENTS = List.of("99000000001", "99000000002");
  private static final int FIVE_MB = 5 * 1024 * 1024;

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
  @Autowired private EffectiveRulesService effectiveRules;
  @Autowired private FrmSnapshotRepository snapshots;

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
      for (String respondent : RESPONDENTS) {
        jdbc.update("DELETE FROM " + C_TABLE + " WHERE C_C = ?", respondent);
      }
    } finally {
      fixture.remove();
    }
  }

  @Test
  void importRejectsWhenDatabaseDiffersFromManifest() throws Exception {
    fixture.installPublished("fixture");
    byte[] zip = download("anio");
    RuleSet before = rulesFiles.load("EDIN", 2027);
    Map<String, Object> help =
        snapshots.snapshot("EDIN", 2027).rows().get(FrmTable.PRE_AYUDA).get(0);
    Object helpId = help.get("COD_AYU");
    try {
      jdbc.update("UPDATE FRM_PRE_AYUDA SET TEXTO = 'Editado a mano' WHERE COD_AYU = ?", helpId);
      jdbc.execute("ALTER TABLE " + H_TABLE + " DROP COLUMN H02A");
      answerColumns.evict(H_TABLE);

      importPackage(zip, false)
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.detail").value("La base de datos no coincide con el paquete."))
          .andExpect(
              jsonPath("$.differences")
                  .value(
                      hasItems(
                          "FRM_PRE_AYUDA: el contenido no coincide.",
                          "Falta la columna " + H_TABLE + ".H02A.")));
      importPackage(zip, true).andExpect(status().isUnprocessableContent());

      assertThat(rulesFiles.load("EDIN", 2027)).isEqualTo(before);
    } finally {
      jdbc.update(
          "UPDATE FRM_PRE_AYUDA SET TEXTO = ? WHERE COD_AYU = ?", help.get("TEXTO"), helpId);
      jdbc.execute("ALTER TABLE " + H_TABLE + " ADD (H02A VARCHAR2(50))");
      answerColumns.evict(H_TABLE);
    }
  }

  @Test
  void importRejectsTamperedOrHostileZip() throws Exception {
    fixture.installPublished("fixture");
    byte[] zip = download("anio");
    Map<String, byte[]> entries = PackageZips.read(zip);
    RuleSet before = rulesFiles.load("EDIN", 2027);
    Set<String> filesBefore = rulesDirectoryFiles();

    Map<String, byte[]> edited = new LinkedHashMap<>(entries);
    edited.put(
        "reglas.json",
        (new String(entries.get("reglas.json"), StandardCharsets.UTF_8) + " ")
            .getBytes(StandardCharsets.UTF_8));
    assertThat(edited.get("reglas.json")).isNotEqualTo(entries.get("reglas.json"));
    importPackage(PackageZips.write(edited), true)
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.detail").value("Los archivos del paquete no coinciden con el manifiesto."))
        .andExpect(jsonPath("$.differences").value(hasItem("reglas.json")));

    Map<String, byte[]> extra = new LinkedHashMap<>(entries);
    extra.put("extra.txt", "hola".getBytes(StandardCharsets.US_ASCII));
    importPackage(PackageZips.write(extra), true)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.differences").value(hasItem("Entrada no esperada: extra.txt")));

    Map<String, byte[]> missing = new LinkedHashMap<>(entries);
    missing.remove("columnas.sql");
    importPackage(PackageZips.write(missing), true)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.differences").value(hasItem("Falta la entrada: columnas.sql")));

    for (String hostileName : List.of("../x", "/etc/x", "sub/frm.sql", "..\\x", "C:x")) {
      Map<String, byte[]> traversal = new LinkedHashMap<>(entries);
      traversal.put(hostileName, "x".getBytes(StandardCharsets.US_ASCII));
      importPackage(PackageZips.write(traversal), true)
          .andExpect(status().isUnprocessableContent())
          .andExpect(
              jsonPath("$.differences").value(hasItem("Hay una entrada con un nombre no válido.")));
    }

    importPackage(PackageZips.withDuplicate(entries, "frm.sql"), true)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.differences").value(hasItem("Entrada repetida: frm.sql")));

    byte[] tooLarge = new byte[FIVE_MB + 1];
    new Random(7).nextBytes(tooLarge);
    importPackage(tooLarge, true)
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.detail").value("El paquete supera 5 MB."));

    Map<String, byte[]> bomb = new LinkedHashMap<>(entries);
    bomb.put("frm.sql", new byte[21 * 1024 * 1024]);
    byte[] bombZip = PackageZips.write(bomb);
    assertThat(bombZip.length).isLessThan(FIVE_MB);
    importPackage(bombZip, true)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("El paquete supera 20 MB descomprimido."));

    importPackage("no es un zip".getBytes(StandardCharsets.US_ASCII), true)
        .andExpect(status().isUnprocessableContent());

    assertThat(rulesFiles.load("EDIN", 2027)).isEqualTo(before);
    assertThat(rulesDirectoryFiles()).isEqualTo(filesBefore);
  }

  /** Los datos de desarrollo traen "página" en FRM_PRE_AYUDA: el paquete tiene texto no ASCII. */
  @Test
  void importRejectsADifferentCharacterSetOnlyWithNonAsciiText() throws Exception {
    fixture.installPublished("fixture");
    Map<String, byte[]> accented = PackageZips.read(download("anio"));

    importPackage(PackageZips.write(withCharacterSet(accented, "WE8MSWIN1252")), false)
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.detail")
                .value("Juego de caracteres distinto: origen WE8MSWIN1252, destino AL32UTF8"));
    importPackage(PackageZips.write(accented), false).andExpect(status().isOk());

    List<Map<String, Object>> helps =
        snapshots.snapshot("EDIN", 2027).rows().get(FrmTable.PRE_AYUDA);
    try {
      for (Map<String, Object> help : helps) {
        jdbc.update(
            "UPDATE FRM_PRE_AYUDA SET TEXTO = 'Ayuda en ASCII' WHERE COD_AYU = ?",
            help.get("COD_AYU"));
      }
      Map<String, byte[]> ascii = PackageZips.read(download("anio"));
      assertThat(new String(ascii.get("frm.sql"), StandardCharsets.US_ASCII))
          .doesNotContain("RAISE_APPLICATION_ERROR(-20002");

      importPackage(PackageZips.write(withCharacterSet(ascii, "WE8MSWIN1252")), false)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.alreadyImported").value(true));
    } finally {
      for (Map<String, Object> help : helps) {
        jdbc.update(
            "UPDATE FRM_PRE_AYUDA SET TEXTO = ? WHERE COD_AYU = ?",
            help.get("TEXTO"),
            help.get("COD_AYU"));
      }
    }
  }

  @Test
  void previewShowsImpactCountsWithoutRespondentCodes() throws Exception {
    fixture.installPublished("fixture");
    RuleSet published = rulesFiles.load("EDIN", 2027);
    correctSkip(2);
    byte[] zip = download("correccion");
    restoreRulesFile(published);
    String firstColumn =
        QuestionnaireService.activeItemColumns(
                questionnaire.pageEntries(SurveyPage.find("C1").orElseThrow(), "EDIN", 2027))
            .get(0);
    long baseline = savedCount(firstColumn);
    for (String respondent : RESPONDENTS) {
      jdbc.update(
          "INSERT INTO "
              + C_TABLE
              + " (C_C, ESTADO, TC, "
              + firstColumn
              + ") VALUES (?, 1, 1, '1')",
          respondent);
    }

    String body =
        importPackage(zip, false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.kind").value("correccion"))
            .andExpect(jsonPath("$.alreadyImported").value(false))
            // El archivo vigente lo dejó la publicación de este ambiente, no una importación.
            .andExpect(jsonPath("$.overwritesLocalChange").value(true))
            .andExpect(jsonPath("$.impact.length()").value(1))
            .andExpect(jsonPath("$.impact[0].pageCode").value("C1"))
            .andExpect(jsonPath("$.impact[0].savedCount").value(baseline + 2))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    for (String respondent : RESPONDENTS) {
      assertThat(body).doesNotContain(respondent);
    }
    assertThat(body).doesNotContain("C_C");
    // La vista previa no escribe nada.
    assertThat(rulesFiles.load("EDIN", 2027)).isEqualTo(published);
  }

  @Test
  void confirmWritesRulesKeepsVersionAndEvicts(CapturedOutput output) throws Exception {
    fixture.installPublished("fixture");
    RuleSet published = rulesFiles.load("EDIN", 2027);
    correctSkip(2);
    RuleSet corrected = rulesFiles.load("EDIN", 2027);
    byte[] zip = download("correccion");
    restoreRulesFile(published);
    assertThat(effectiveRules.effectiveRules("EDIN", 2027).version()).isEqualTo(2);
    long generation = questionnaire.cacheGeneration();
    FrmSnapshot frmBefore = snapshots.snapshot("EDIN", 2027);

    importPackage(zip, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3))
        .andExpect(jsonPath("$.kind").value("correccion"))
        .andExpect(jsonPath("$.alreadyImported").value(false));

    RuleSet imported = rulesFiles.load("EDIN", 2027);
    assertThat(imported.version()).isEqualTo(3);
    assertThat(imported.withLastChange(null)).isEqualTo(corrected.withLastChange(null));
    assertThat(imported.lastChange().operation()).isEqualTo("importar-paquete");
    assertThat(imported.lastChange().user()).isEqualTo("admin:adm.cuest");
    assertThat(questionnaire.cacheGeneration()).isGreaterThan(generation);
    assertThat(effectiveRules.effectiveRules("EDIN", 2027).version()).isEqualTo(3);
    String manifestCode =
        "m" + PackageZips.sha256(PackageZips.read(zip).get("manifiesto.json")).substring(0, 12);
    assertThat(output.getAll())
        .contains("operacion=importar-paquete")
        .contains("codigos=[" + manifestCode + "]");
    // La importación solo lee FRM_*.
    assertThat(snapshots.snapshot("EDIN", 2027)).isEqualTo(frmBefore);
  }

  @Test
  void importCreatesTheYearWhenThisEnvironmentDoesNotHaveIt() throws Exception {
    fixture.installPublished("fixture");
    byte[] zip = download("anio");
    RuleSet published = rulesFiles.load("EDIN", 2027);
    Files.delete(rulesFile());
    rulesFiles.evict();

    importPackage(zip, false)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.impact.length()").value(0))
        .andExpect(jsonPath("$.alreadyImported").value(false));
    assertThat(Files.exists(rulesFile())).isFalse();

    importPackage(zip, true).andExpect(status().isOk());

    RuleSet imported = rulesFiles.load("EDIN", 2027);
    assertThat(imported.withLastChange(null)).isEqualTo(published.withLastChange(null));
    assertThat(imported.lastChange().operation()).isEqualTo("importar-paquete");
  }

  @Test
  void reimportIsANoOp() throws Exception {
    fixture.installPublished("fixture");
    byte[] zip = download("anio");
    RuleSet before = rulesFiles.load("EDIN", 2027);
    int historyBefore = rulesFiles.history("EDIN", 2027).size();

    for (boolean confirm : List.of(false, true)) {
      long generation = questionnaire.cacheGeneration();
      importPackage(zip, confirm)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.version").value(2))
          .andExpect(jsonPath("$.alreadyImported").value(true))
          .andExpect(jsonPath("$.overwritesLocalChange").value(false))
          .andExpect(jsonPath("$.impact.length()").value(0));
      // Confirmar un paquete ya importado igual descarta las cachés.
      if (confirm) {
        assertThat(questionnaire.cacheGeneration()).isGreaterThan(generation);
      } else {
        assertThat(questionnaire.cacheGeneration()).isEqualTo(generation);
      }
    }

    assertThat(rulesFiles.load("EDIN", 2027)).isEqualTo(before);
    assertThat(rulesFiles.history("EDIN", 2027)).hasSize(historyBefore);
  }

  @Test
  void olderPackageIs409() throws Exception {
    fixture.installPublished("fixture");
    byte[] zip = download("anio");
    correctSkip(2);
    RuleSet corrected = rulesFiles.load("EDIN", 2027);

    importPackage(zip, true)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("La versión del paquete (2) no es posterior a la de este ambiente (3)."));

    assertThat(rulesFiles.load("EDIN", 2027)).isEqualTo(corrected);
  }

  /** Tras una importación, el paquete siguiente no pisa ningún cambio hecho aquí. */
  @Test
  void overwritesLocalChangeOnlyWhenTheFileWasEditedHere() throws Exception {
    fixture.installPublished("fixture");
    byte[] year = download("anio");
    correctSkip(2);
    byte[] correction = download("correccion");
    Files.delete(rulesFile());
    rulesFiles.evict();
    importPackage(year, true).andExpect(status().isOk());

    importPackage(correction, false)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3))
        .andExpect(jsonPath("$.overwritesLocalChange").value(false))
        .andExpect(jsonPath("$.impact[0].pageCode").value("C1"));
  }

  @Test
  void manifestListingFewerColumnsThanTheYearIsRejected() throws Exception {
    fixture.installPublished("fixture");
    Map<String, byte[]> entries = PackageZips.read(download("anio"));
    ObjectNode manifest = (ObjectNode) jsonMapper.readTree(entries.get("manifiesto.json"));
    manifest.putObject("columnasRequeridas");
    entries.put("manifiesto.json", jsonMapper.writeValueAsBytes(manifest));

    importPackage(PackageZips.write(entries), true)
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.detail").value("El manifiesto no coincide con las columnas del año."))
        .andExpect(jsonPath("$.differences").value(hasItem(H_TABLE + ".H02A")));
  }

  /** Reglas editadas a mano con el hash al día: la puerta de publicación las rechaza aquí. */
  @Test
  void coherentlyEditedRulesMustStillPassThePublicationGate() throws Exception {
    fixture.installPublished("fixture");
    RuleSet before = rulesFiles.load("EDIN", 2027);
    Map<String, byte[]> entries = PackageZips.read(download("anio"));

    Map<String, byte[]> brokenSkip =
        withRules(
            entries,
            rules ->
                ((ArrayNode) rules.get("saltos"))
                    .add(
                        jsonMapper.readTree(
                            """
                            {"id":"apunta-a-z99","si":{"pregunta":"Z99","op":"eq","valor":"1"},
                             "entonces":{"accion":"deshabilitar","preguntas":["C01B"]}}""")));
    importPackage(PackageZips.write(brokenSkip), false)
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.detail").value("reglas.json no pasa la lista de publicación en esta base."))
        .andExpect(jsonPath("$.differences").value(hasItem(startsWith("SIN_ERRORES"))));

    assertThat(before.cases()).isNotEmpty();
    Map<String, byte[]> failingCase =
        withRules(
            entries,
            rules ->
                ((ObjectNode) rules.get("casos").get(0)).putObject("esperado").putArray("ocultas"));
    importPackage(PackageZips.write(failingCase), false)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.differences").value(hasItem(startsWith("CASOS_EN_VERDE"))));

    Map<String, byte[]> withoutPublication =
        withRules(entries, rules -> rules.remove("publicacion"));
    importPackage(PackageZips.write(withoutPublication), false)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("reglas.json no trae una publicación válida."));

    assertThat(rulesFiles.load("EDIN", 2027)).isEqualTo(before);
  }

  @Test
  void importOfAnotherYearOrRulesIsRejected() throws Exception {
    fixture.installPublished("fixture");
    byte[] zip = download("anio");

    multipartImport("/api/v1/admin/questionnaire/EDIN/2028/package/import", zip, false)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("El paquete es de otra encuesta o año."));
    multipartImport("/api/v1/admin/questionnaire/EDID/2027/package/import", zip, false)
        .andExpect(status().isUnprocessableContent());
  }

  private void correctSkip(int version) throws Exception {
    MockHttpServletRequestBuilder request =
        put(YEAR + "/rules/skips/c01a-si-oculta-c01b")
            .contentType(MediaType.APPLICATION_JSON)
            .content(HARMLESS_SKIP.formatted(version, REASON));
    mockMvc.perform(request.with(realCsrf(mockMvc)).cookie(admin())).andExpect(status().isOk());
  }

  private byte[] download(String kind) throws Exception {
    return mockMvc
        .perform(get(PACKAGE).param("tipo", kind).cookie(admin()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
  }

  private ResultActions importPackage(byte[] zip, boolean confirm) throws Exception {
    return multipartImport(IMPORT, zip, confirm);
  }

  private ResultActions multipartImport(String path, byte[] zip, boolean confirm) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("archivo", "paquete.zip", "application/zip", zip);
    return mockMvc.perform(
        multipart(path)
            .file(file)
            .param("confirmar", String.valueOf(confirm))
            .with(realCsrf(mockMvc))
            .cookie(admin()));
  }

  private Map<String, byte[]> withCharacterSet(Map<String, byte[]> entries, String characterSet) {
    ObjectNode manifest = (ObjectNode) jsonMapper.readTree(entries.get("manifiesto.json"));
    manifest.put("juegoCaracteres", characterSet);
    Map<String, byte[]> changed = new LinkedHashMap<>(entries);
    changed.put("manifiesto.json", jsonMapper.writeValueAsBytes(manifest));
    return changed;
  }

  /** Cambia reglas.json y deja su SHA-256 al día en el manifiesto: una edición coherente. */
  private Map<String, byte[]> withRules(Map<String, byte[]> entries, Consumer<ObjectNode> change) {
    ObjectNode rules = (ObjectNode) jsonMapper.readTree(entries.get("reglas.json"));
    change.accept(rules);
    byte[] rulesBytes = jsonMapper.writeValueAsBytes(rules);
    ObjectNode manifest = (ObjectNode) jsonMapper.readTree(entries.get("manifiesto.json"));
    ((ObjectNode) manifest.get("archivos")).put("reglas.json", PackageZips.sha256(rulesBytes));
    Map<String, byte[]> changed = new LinkedHashMap<>(entries);
    changed.put("reglas.json", rulesBytes);
    changed.put("manifiesto.json", jsonMapper.writeValueAsBytes(manifest));
    return changed;
  }

  /** El ambiente "atrasado": el archivo de reglas vuelve a una versión anterior. */
  private void restoreRulesFile(RuleSet older) throws IOException {
    Files.write(rulesFile(), jsonMapper.writeValueAsBytes(older));
    rulesFiles.evict();
    effectiveRules.evict();
  }

  private long savedCount(String column) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + C_TABLE + " WHERE " + column + " IS NOT NULL", Long.class);
    return count == null ? 0 : count;
  }

  private Path rulesFile() {
    return Path.of(survey.rulesDir()).resolve("EDIN-2027.json");
  }

  private Set<String> rulesDirectoryFiles() throws IOException {
    Set<String> names = new HashSet<>();
    try (Stream<Path> files = Files.walk(Path.of(survey.rulesDir()))) {
      files.forEach(file -> names.add(file.toString()));
    }
    return names;
  }

  private Cookie admin() {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(
            SessionPrincipal.administrator(
                "adm.cuest", EnumSet.of(Role.ADMIN_CUESTIONARIO), null)));
  }
}
