package co.gov.dane.edi.operation.keys;

import static co.gov.dane.edi.support.CsrfTestSupport.realCsrf;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_AT_SIGN_ENTITY;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_EDID_COMPLETE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_INACTIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NO_CATALOG_WITH_KEY;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NO_KEY_DMC;
import static co.gov.dane.edi.support.OperationalSampleFixture.CASE_NO_KEY_WEB;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_ANTIOQUIA;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_ATLANTICO;
import static co.gov.dane.edi.support.OperationalSampleFixture.DEPARTMENT_VALLE;
import static co.gov.dane.edi.support.OperationalSampleFixture.ENTITY_EDID;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_ENGINEER;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_OPERATIVE;
import static co.gov.dane.edi.support.OperationalSampleFixture.USER_TERRITORIAL_MULTI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.admin.AdminAudit;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.AccountType;
import co.gov.dane.edi.model.LoginRequest;
import co.gov.dane.edi.operation.OperationalAccess;
import co.gov.dane.edi.operation.OperationalAccessService;
import co.gov.dane.edi.operation.keys.dto.KeysGenerateRequest;
import co.gov.dane.edi.security.AuthService;
import co.gov.dane.edi.security.Role;
import co.gov.dane.edi.security.SessionPrincipal;
import co.gov.dane.edi.security.SessionTokenService;
import co.gov.dane.edi.support.OperationalSampleFixture;
import co.gov.dane.edi.support.OracleIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Generación y descarga de claves de la muestra. Corre con el cuestionario en solo lectura (como
 * producción): las claves deben funcionar igual.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "app.admin.questionnaire-writes-enabled=false")
class SampleKeysIT extends OracleIntegrationTest {

  private static final String BASE = "/api/v1/admin/keys";

  /** Funcionarios extra (rango del fixture, que {@code clear} borra): web activos sin clave. */
  private static final List<String> EXTRA_WEB_WITHOUT_KEY =
      List.of("900000015", "900000016", "900000017");

  /** Inactivo, web y sin clave: no debe recibir clave. */
  private static final String EXTRA_INACTIVE_WITHOUT_KEY = "900000018";

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionTokenService tokens;
  @Autowired private OperationalAccessService accessService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SurveyProperties survey;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private AuthService authService;
  @Autowired private SampleKeysRepository repository;
  @Autowired private AdminAudit audit;
  @Autowired private TransactionTemplate transactions;

  private OperationalSampleFixture fixture;

  @BeforeEach
  void loadFixture() {
    fixture = new OperationalSampleFixture(jdbc);
    fixture.load(survey.year());
    for (String code : EXTRA_WEB_WITHOUT_KEY) {
      insertRespondent(code, DEPARTMENT_ATLANTICO, 1, 1);
    }
    insertRespondent(EXTRA_INACTIVE_WITHOUT_KEY, DEPARTMENT_ATLANTICO, 0, 1);
    accessService.clearCache();
  }

  @AfterEach
  void clearFixture() {
    fixture.clear(survey.year());
    accessService.clearCache();
  }

  @Test
  void summaryCountsEligibleWithAndWithoutKeyByDepartment() throws Exception {
    getAs(national(), "/summary?anio=" + survey.year())
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(jsonPath("$.year").value(survey.year()))
        .andExpect(
            departmentValue(DEPARTMENT_ANTIOQUIA, "name").value("DEPARTMENT_ANTIOQUIA (ficticio)"))
        .andExpect(departmentValue(DEPARTMENT_ANTIOQUIA, "eligible").value(7))
        .andExpect(departmentValue(DEPARTMENT_ANTIOQUIA, "withKey").value(7))
        .andExpect(departmentValue(DEPARTMENT_ANTIOQUIA, "withoutKey").value(0))
        .andExpect(departmentValue(DEPARTMENT_ATLANTICO, "eligible").value(5))
        .andExpect(departmentValue(DEPARTMENT_ATLANTICO, "withKey").value(2))
        .andExpect(departmentValue(DEPARTMENT_ATLANTICO, "withoutKey").value(3))
        .andExpect(departmentValue(DEPARTMENT_VALLE, "eligible").value(2))
        .andExpect(departmentValue(DEPARTMENT_VALLE, "withKey").value(1))
        .andExpect(departmentValue(DEPARTMENT_VALLE, "withoutKey").value(1));
    // Año por defecto: el configurado.
    getAs(national(), "/summary")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.year").value(survey.year()));
  }

  @Test
  void generateAssignsKeysOnlyToActiveWebRespondentsWithoutKey(CapturedOutput output)
      throws Exception {
    Map<String, String> before = credentialsOfFixture();
    long pending = pendingWithoutKey();

    postAs(national(), "/generate", generateBody(survey.year(), pending))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(jsonPath("$.generated").value(pending));

    Map<String, String> after = credentialsOfFixture();
    List<String> receivers =
        List.of(
            CASE_NO_KEY_WEB,
            EXTRA_WEB_WITHOUT_KEY.get(0),
            EXTRA_WEB_WITHOUT_KEY.get(1),
            EXTRA_WEB_WITHOUT_KEY.get(2));
    for (String code : receivers) {
      assertThat(after.get(code)).matches("[A-Z]{8}/[A-Z]{8}");
    }
    // DMC, inactivos y quienes ya tenían clave quedan como estaban.
    for (Map.Entry<String, String> entry : before.entrySet()) {
      if (!receivers.contains(entry.getKey())) {
        assertThat(after.get(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
      }
    }
    assertThat(after.get(CASE_NO_KEY_DMC)).isEqualTo("null/null");
    assertThat(after.get(EXTRA_INACTIVE_WITHOUT_KEY)).isEqualTo("null/null");
    assertThat(after.get(CASE_INACTIVE)).isEqualTo(before.get(CASE_INACTIVE));
    assertThat(duplicatedLoginsOfTheYear()).isZero();
    assertThat(pendingWithoutKey()).isZero();

    // Una segunda generación no encuentra a nadie y no cambia nada.
    postAs(national(), "/generate", generateBody(survey.year(), 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generated").value(0));
    assertThat(credentialsOfFixture()).isEqualTo(after);

    // El encuestado entra con la clave generada (año configurado, TC1 = 1, ATV = 1).
    String[] credential = after.get(CASE_NO_KEY_WEB).split("/");
    SessionPrincipal principal =
        authService.authenticate(
            new LoginRequest(AccountType.RESPONDENT, credential[0], credential[1]), "127.0.0.9");
    assertThat(principal.respondentCode()).isEqualTo(CASE_NO_KEY_WEB);

    // Descarga del departamento 8: incluye las claves generadas allí.
    MvcResult excel =
        getAs(national(), "/excel?anio=" + survey.year() + "&departamento=" + DEPARTMENT_ATLANTICO)
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andReturn();
    try (XSSFWorkbook workbook = open(excel)) {
      Sheet sheet = workbook.getSheet("Claves");
      for (String code : EXTRA_WEB_WITHOUT_KEY) {
        String[] generated = after.get(code).split("/");
        Row row = rowByRespondent(sheet, code);
        assertThat(row.getCell(4).getStringCellValue()).isEqualTo(generated[0]);
        assertThat(row.getCell(5).getStringCellValue()).isEqualTo(generated[1]);
      }
    }

    String logs = output.getAll();
    assertThat(logs).contains("operacion=keys-generate codigos=[" + pending + "]");
    assertThat(logs).contains("operacion=keys-generate codigos=[0]");
    assertThat(logs).contains("operacion=keys-download codigos=[" + DEPARTMENT_ATLANTICO + "]");
    for (String code : receivers) {
      for (String value : after.get(code).split("/")) {
        assertThat(logs).doesNotContain(value);
      }
    }
    for (String code : EXTRA_WEB_WITHOUT_KEY) {
      assertThat(logs).doesNotContain(code);
    }
  }

  @Test
  void staleExpectedCountIsAConflictAndChangesNothing() throws Exception {
    Map<String, String> before = credentialsOfFixture();
    long pending = pendingWithoutKey();
    MvcResult conflict =
        postAs(national(), "/generate", generateBody(survey.year(), pending + 1))
            .andExpect(status().isConflict())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
            .andReturn();
    assertThat(conflict.getResponse().getContentAsString()).doesNotContain("9000000");
    assertThat(credentialsOfFixture()).isEqualTo(before);
  }

  @Test
  void invalidRequestsAreRejected() throws Exception {
    postAs(national(), "/generate", generateBody(survey.year(), -1))
        .andExpect(status().isBadRequest());
    postAs(national(), "/generate", generateBody(0, 0)).andExpect(status().isBadRequest());
    getAs(national(), "/excel?departamento=0").andExpect(status().isBadRequest());
    // Departamento sin funcionarios elegibles en la muestra del año: no existe para las claves.
    getAs(national(), "/excel?departamento=99")
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void excelHasTheEligibleWithKeyOfTheRequestedDepartmentOnly() throws Exception {
    MvcResult excel =
        getAs(national(), "/excel?departamento=" + DEPARTMENT_ATLANTICO)
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"claves-general-"
                            + survey.year()
                            + "-dpto08-"
                            + LocalDate.now(ZoneId.of("America/Bogota"))
                            + ".xlsx\""))
            .andReturn();
    try (XSSFWorkbook workbook = open(excel)) {
      Sheet sheet = workbook.getSheet("Claves");
      Row headers = sheet.getRow(1);
      List<String> names = new java.util.ArrayList<>();
      headers.forEach(cell -> names.add(cell.getStringCellValue()));
      assertThat(names)
          .containsExactly("Entidad", "Código entidad", "Cédula", "Nombre", "Usuario", "Clave");
      // Antes de generar: solo los dos elegibles del 8 que ya tenían clave.
      assertThat(sheet.getLastRowNum()).isEqualTo(3);
      Row edid = rowByRespondent(sheet, CASE_EDID_COMPLETE);
      assertThat(edid.getCell(1).getNumericCellValue()).isEqualTo(ENTITY_EDID);
      assertThat(edid.getCell(3).getStringCellValue()).isEqualTo("Persona Ficticia 08");
      assertThat(edid.getCell(4).getStringCellValue()).isEqualTo("FX000008");
      assertThat(edid.getCell(5).getStringCellValue()).isEqualTo("Fx#00008");
      assertThat(rowByRespondent(sheet, CASE_AT_SIGN_ENTITY).getCell(0).getStringCellValue())
          .isEqualTo("@Ficticia");
      assertThat(findRow(sheet, CASE_NO_CATALOG_WITH_KEY)).isNull();
    }
  }

  @Test
  void generationIsAllowedOnlyToTheNationalAdministrator() throws Exception {
    Map<String, String> before = credentialsOfFixture();
    for (Cookie cookie :
        List.of(
            session(USER_TERRITORIAL_MULTI, Role.ADMIN_TERRITORIAL, "5"),
            session(USER_OPERATIVE, Role.ADMIN_OPERATIVO, null))) {
      getAs(cookie, "/summary").andExpect(status().isForbidden());
      postAs(cookie, "/generate", generateBody(survey.year(), 4)).andExpect(status().isForbidden());
      getAs(cookie, "/excel?departamento=8").andExpect(status().isForbidden());
    }
    mockMvc
        .perform(
            post(BASE + "/generate")
                .cookie(national())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(generateBody(survey.year(), 4))))
        .andExpect(status().isForbidden());
    assertThat(credentialsOfFixture()).isEqualTo(before);
  }

  @Test
  void serviceRequiresKeyManagement() {
    SampleKeysService service = serviceWith(new CredentialGenerator(new Random(1L)));
    OperationalAccess withoutKeys = new OperationalAccess(true, List.of(), false, false);
    assertThatThrownBy(() -> service.summary(withoutKeys, survey.year()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                service.generate(
                    withoutKeys, USER_ENGINEER, new KeysGenerateRequest(survey.year(), 4)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.departmentKeys(withoutKeys, USER_ENGINEER, survey.year(), 8))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void collisionsWithExistingLoginsAreRetriedAndDuplicatedRowsGetTheirOwnKey() {
    // Mismo C_C dos veces en el año (dato heredado): cada fila recibe su propio usuario.
    insertRespondent("900000019", DEPARTMENT_VALLE, 1, 1);
    insertRespondent("900000019", DEPARTMENT_VALLE, 1, 1);
    jdbc.update(
        "UPDATE EDIN_MUESTRA SET LGN = 'TAKENAAA', PSW = 'TAKENBBB' WHERE C_C = ? AND ANIO = ?",
        EXTRA_INACTIVE_WITHOUT_KEY,
        survey.yearText());
    Deque<String> values = new ArrayDeque<>();
    // Usuarios: el primero choca con uno existente y el segundo con uno recién generado.
    values.addAll(
        List.of(
            "TAKENAAA",
            "LOGINAAA",
            "PASSWORD",
            "LOGINAAA",
            "LOGINBBB",
            "PASSWORD",
            "LOGINCCC",
            "PASSWORD",
            "LOGINDDD",
            "PASSWORD",
            "LOGINEEE",
            "PASSWORD",
            "LOGINFFF",
            "PASSWORD"));
    SampleKeysService service = serviceWith(new SequenceGenerator(values));
    long pending = pendingWithoutKey();
    assertThat(pending).isEqualTo(6);

    long generated =
        transactions.execute(
            status ->
                service
                    .generate(
                        nationalAccess(),
                        USER_ENGINEER,
                        new KeysGenerateRequest(survey.year(), pending))
                    .generated());

    assertThat(generated).isEqualTo(6);
    assertThat(values).isEmpty();
    assertThat(
            jdbc.queryForList(
                "SELECT LGN FROM EDIN_MUESTRA WHERE C_C = ? AND ANIO = ? ORDER BY LGN",
                String.class,
                "900000019",
                survey.yearText()))
        .hasSize(2)
        .doesNotHaveDuplicates()
        .allMatch(login -> login.startsWith("LOGIN"));
    assertThat(duplicatedLoginsOfTheYear()).isZero();
  }

  @Test
  void generatorThatOnlyRepeatsExistingLoginsFailsWithoutWriting() {
    jdbc.update(
        "UPDATE EDIN_MUESTRA SET LGN = 'TAKENAAA', PSW = 'TAKENBBB' WHERE C_C = ? AND ANIO = ?",
        EXTRA_INACTIVE_WITHOUT_KEY,
        survey.yearText());
    Map<String, String> before = credentialsOfFixture();
    SampleKeysService service = serviceWith(new ConstantGenerator("TAKENAAA"));
    long pending = pendingWithoutKey();
    assertThatThrownBy(
            () ->
                transactions.execute(
                    status ->
                        service.generate(
                            nationalAccess(),
                            USER_ENGINEER,
                            new KeysGenerateRequest(survey.year(), pending))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("TAKENAAA");
    assertThat(credentialsOfFixture()).isEqualTo(before);
  }

  // ---- utilidades -------------------------------------------------------------------------

  /** Generador de prueba que devuelve una secuencia fija. */
  private static final class SequenceGenerator extends CredentialGenerator {
    private final Deque<String> values;

    SequenceGenerator(Deque<String> values) {
      super(new Random(0L));
      this.values = values;
    }

    @Override
    public String next() {
      return values.removeFirst();
    }
  }

  /** Generador de prueba que siempre devuelve el mismo valor. */
  private static final class ConstantGenerator extends CredentialGenerator {
    private final String value;

    ConstantGenerator(String value) {
      super(new Random(0L));
      this.value = value;
    }

    @Override
    public String next() {
      return value;
    }
  }

  private SampleKeysService serviceWith(CredentialGenerator generator) {
    return new SampleKeysService(repository, generator, survey, audit);
  }

  private OperationalAccess nationalAccess() {
    return new OperationalAccess(true, List.of(), false, true);
  }

  private long pendingWithoutKey() throws RuntimeException {
    try {
      String json =
          getAs(national(), "/summary?anio=" + survey.year())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      long total = 0;
      for (JsonNode department : jsonMapper.readTree(json).get("departments")) {
        total += department.get("withoutKey").asLong();
      }
      return total;
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private Map<String, String> credentialsOfFixture() {
    Map<String, String> credentials = new HashMap<>();
    jdbc.query(
        "SELECT C_C, LGN, PSW FROM EDIN_MUESTRA WHERE ANIO = ? AND C_C BETWEEN '900000001' AND"
            + " '900000099'",
        row -> {
          credentials.put(row.getString("C_C"), row.getString("LGN") + "/" + row.getString("PSW"));
        },
        survey.yearText());
    return credentials;
  }

  private int duplicatedLoginsOfTheYear() {
    Integer duplicated =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM (SELECT LGN FROM EDIN_MUESTRA WHERE ANIO = ? AND LGN IS NOT NULL"
                + " GROUP BY LGN HAVING COUNT(*) > 1)",
            Integer.class,
            survey.yearText());
    return duplicated == null ? 0 : duplicated;
  }

  private void insertRespondent(String code, int department, int active, int channel) {
    jdbc.update(
        "INSERT INTO EDIN_MUESTRA (ANIO, COD_ENT, COD_DEPTO, C_C, NOM_APE, ATV, TC1, ENC,"
            + " AREA_TRA) VALUES (?, ?, ?, ?, ?, ?, ?, '1', 1)",
        survey.yearText(),
        String.valueOf(ENTITY_EDID),
        String.valueOf(department),
        code,
        "Persona Ficticia " + code.substring(code.length() - 2),
        active,
        channel);
  }

  private static org.springframework.test.web.servlet.result.JsonPathResultMatchers departmentValue(
      int code, String field) {
    return jsonPath("$.departments[?(@.code == " + code + ")]." + field);
  }

  private static XSSFWorkbook open(MvcResult result) throws Exception {
    return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
  }

  private static Row rowByRespondent(Sheet sheet, String code) {
    Row row = findRow(sheet, code);
    assertThat(row).as("fila de " + code).isNotNull();
    return row;
  }

  private static Row findRow(Sheet sheet, String code) {
    for (int index = 2; index <= sheet.getLastRowNum(); index++) {
      Row row = sheet.getRow(index);
      if (row != null && code.equals(row.getCell(2).getStringCellValue())) {
        return row;
      }
    }
    return null;
  }

  private Map<String, Object> generateBody(int year, long expected) {
    return Map.of("anio", year, "expectedWithoutKey", expected);
  }

  private Cookie national() {
    return session(USER_ENGINEER, Role.ADMIN_NACIONAL, null);
  }

  private Cookie session(String username, Role role, String department) {
    return new Cookie(
        SessionTokenService.SESSION_COOKIE_NAME,
        tokens.issueToken(SessionPrincipal.administrator(username, EnumSet.of(role), department)));
  }

  private ResultActions getAs(Cookie cookie, String path) throws Exception {
    return mockMvc.perform(get(BASE + path).cookie(cookie));
  }

  private ResultActions postAs(Cookie cookie, String path, Map<String, Object> body)
      throws Exception {
    return mockMvc.perform(
        post(BASE + path)
            .with(realCsrf(mockMvc))
            .cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(body)));
  }
}
