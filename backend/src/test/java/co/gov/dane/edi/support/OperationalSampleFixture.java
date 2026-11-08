package co.gov.dane.edi.support;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Muestra operativa FICTICIA para las pruebas de la Fase 4 (reportes, novedades y claves). Cubre
 * cada estado del criterio de la spec §5.1 y los casos límite de los listados. Las cédulas son
 * 9000000NN (rango propio, sin choque con db/dev/data.sql, que usa 1000000xxx, departamentos 11 y
 * 25 y entidades 899998/899999). Se inserta con SQL parametrizado y {@link #clear(int)} borra todo
 * lo que {@link #load(int)} creó, sin tocar las filas de data.sql.
 *
 * <p>Los usuarios y los códigos de novedad 3 y 8 también existen en data.sql; {@code load} los
 * reemplaza y {@code clear} los quita, de modo que una prueba que use el fixture debe cargarlo en
 * cada ejecución.
 */
public final class OperationalSampleFixture {

  public static final int DEPARTMENT_ANTIOQUIA = 5;
  public static final int DEPARTMENT_ATLANTICO = 8;
  public static final int DEPARTMENT_VALLE = 76;

  /** Funcionarios web y DMC (Antioquia). */
  public static final int ENTITY_MIXED = 900001;

  /** Entidad EDID (Atlántico). */
  public static final int ENTITY_EDID = 900002;

  /** Entidad con nombre que empieza por arroba (Atlántico). */
  public static final int ENTITY_AT_SIGN = 900003;

  /** Entidad que NO está en GRAL_ENTI (Valle). */
  public static final int ENTITY_NO_CATALOG = 900099;

  public static final String USER_TERRITORIAL_MULTI = "territorial-multi";
  public static final String USER_TERRITORIAL_SINGLE = "territorial-solo";
  public static final String USER_OPERATIVE = "operativo";
  public static final String USER_ENGINEER = "ingeniero";

  /** Clave ficticia de los cuatro usuarios (GRAL_USUARIOS_ADM.CLAVE VARCHAR2(20)). */
  public static final String USER_PASSWORD = "Fixt#2026x";

  /** Códigos de GRAL_NOVEDAD: 3 es una novedad común y 8 la de "otra causa" (por defecto). */
  public static final int NOVELTY_COMMON = 3;

  public static final int NOVELTY_OTHER_CAUSE = 8;

  public static final String CASE_COMPLETE = "900000001";
  public static final String CASE_COMPLETE_WITH_NOVELTY = "900000002";
  public static final String CASE_INCOMPLETE = "900000003";
  public static final String CASE_NOT_STARTED = "900000004";
  public static final String CASE_NOVELTY_NOT_STARTED = "900000005";
  public static final String CASE_NOVELTY_OTHER_CAUSE = "900000006";
  public static final String CASE_DMC_WITH_KEY = "900000007";
  public static final String CASE_EDID_COMPLETE = "900000008";
  public static final String CASE_INACTIVE = "900000009";
  public static final String CASE_NO_CATALOG_WITH_KEY = "900000010";
  public static final String CASE_NO_KEY_WEB = "900000011";
  public static final String CASE_NO_KEY_DMC = "900000012";
  public static final String CASE_FORMULA_NAME = "900000013";
  public static final String CASE_AT_SIGN_ENTITY = "900000014";

  /** Número de filas de la muestra que inserta {@link #load(int)}. */
  public static final int CASE_COUNT = 14;

  /** Casos límite de {@link #loadEdgeCases(int)}: B07 sin H06 y con novedad (cuenta novedad). */
  public static final String CASE_NOVELTY_AFTER_START = "900000015";

  /** Otra causa guardada con referencias numéricas, como la escribe el formulario heredado. */
  public static final String CASE_ENCODED_OTHER_CAUSE = "900000016";

  /** Funcionario completo en un departamento que no está en GRAL_DEPARTAMENTOS. */
  public static final String CASE_DEPARTMENT_NO_CATALOG = "900000017";

  /** Código de departamento ficticio sin fila en GRAL_DEPARTAMENTOS. */
  public static final int DEPARTMENT_NO_CATALOG = 98;

  public static final String ENCODED_OTHER_CAUSE_TEXT = "&#8220;Motivo ficticio&#8221; citado";
  public static final String DECODED_OTHER_CAUSE_TEXT = "“Motivo ficticio” citado";

  public static final String FORMULA_NAME = "=SUMA(1;2)";
  public static final String OTHER_CAUSE_TEXT = "Motivo ficticio de otra causa";

  private static final List<String> USERNAMES =
      List.of(USER_TERRITORIAL_MULTI, USER_TERRITORIAL_SINGLE, USER_OPERATIVE, USER_ENGINEER);

  private static final String FIRST_CODE = "900000001";
  private static final String LAST_CODE = "900000099";

  /** Una fila de EDIN_MUESTRA y qué capítulos tiene diligenciados. */
  private record SampleCase(
      String code,
      String name,
      int department,
      int entity,
      String survey,
      int level,
      int active,
      int channel,
      Integer novelty,
      String otherCause,
      boolean hasKey,
      boolean startedB,
      boolean finishedH) {}

  private final JdbcTemplate jdbc;

  public OperationalSampleFixture(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Carga la muestra del año indicado; si ya estaba cargada, la reemplaza. */
  public void load(int year) {
    clear(year);
    insertCatalogs();
    insertUsers();
    List<SampleCase> cases =
        List.of(
            new SampleCase(
                CASE_COMPLETE,
                "Persona Ficticia 01",
                5,
                ENTITY_MIXED,
                "1",
                1,
                1,
                1,
                null,
                null,
                true,
                true,
                true),
            new SampleCase(
                CASE_COMPLETE_WITH_NOVELTY,
                "Persona Ficticia 02",
                5,
                ENTITY_MIXED,
                "1",
                2,
                1,
                1,
                NOVELTY_COMMON,
                null,
                true,
                true,
                true),
            new SampleCase(
                CASE_INCOMPLETE,
                "Persona Ficticia 03",
                5,
                ENTITY_MIXED,
                "1",
                3,
                1,
                1,
                null,
                null,
                true,
                true,
                false),
            new SampleCase(
                CASE_NOT_STARTED,
                "Persona Ficticia 04",
                5,
                ENTITY_MIXED,
                null,
                1,
                1,
                1,
                null,
                null,
                true,
                false,
                false),
            new SampleCase(
                CASE_NOVELTY_NOT_STARTED,
                "Persona Ficticia 05",
                5,
                ENTITY_MIXED,
                "1",
                2,
                1,
                1,
                NOVELTY_COMMON,
                null,
                true,
                false,
                false),
            new SampleCase(
                CASE_NOVELTY_OTHER_CAUSE,
                "Persona Ficticia 06",
                5,
                ENTITY_MIXED,
                "1",
                3,
                1,
                1,
                NOVELTY_OTHER_CAUSE,
                OTHER_CAUSE_TEXT,
                true,
                false,
                false),
            new SampleCase(
                CASE_DMC_WITH_KEY,
                "Persona Ficticia 07",
                5,
                ENTITY_MIXED,
                "1",
                1,
                1,
                2,
                null,
                null,
                true,
                true,
                false),
            new SampleCase(
                CASE_EDID_COMPLETE,
                "Persona Ficticia 08",
                8,
                ENTITY_EDID,
                "2",
                2,
                1,
                1,
                null,
                null,
                true,
                true,
                true),
            new SampleCase(
                CASE_INACTIVE,
                "Persona Ficticia 09",
                5,
                ENTITY_MIXED,
                "1",
                1,
                0,
                1,
                null,
                null,
                true,
                false,
                false),
            new SampleCase(
                CASE_NO_CATALOG_WITH_KEY,
                "Persona Ficticia 10",
                76,
                ENTITY_NO_CATALOG,
                "1",
                1,
                1,
                1,
                null,
                null,
                true,
                true,
                false),
            new SampleCase(
                CASE_NO_KEY_WEB,
                "Persona Ficticia 11",
                76,
                ENTITY_NO_CATALOG,
                "1",
                2,
                1,
                1,
                null,
                null,
                false,
                false,
                false),
            new SampleCase(
                CASE_NO_KEY_DMC,
                "Persona Ficticia 12",
                5,
                ENTITY_MIXED,
                "1",
                3,
                1,
                2,
                null,
                null,
                false,
                false,
                false),
            new SampleCase(
                CASE_FORMULA_NAME,
                FORMULA_NAME,
                5,
                ENTITY_MIXED,
                "1",
                1,
                1,
                1,
                null,
                null,
                true,
                false,
                false),
            new SampleCase(
                CASE_AT_SIGN_ENTITY,
                "Persona Ficticia 14",
                8,
                ENTITY_AT_SIGN,
                "1",
                2,
                1,
                1,
                null,
                null,
                true,
                true,
                true));
    for (SampleCase sampleCase : cases) {
      insertSample(year, sampleCase);
    }
  }

  /**
   * Agrega, sobre {@link #load(int)}, tres casos límite que no entran en la carga base para no
   * alterar las cifras que ya verifican las demás pruebas: {@link #CASE_NOVELTY_AFTER_START}
   * (Antioquia, nivel 1, web), {@link #CASE_ENCODED_OTHER_CAUSE} (Antioquia, nivel 2, web, otra
   * causa) y {@link #CASE_DEPARTMENT_NO_CATALOG} (departamento {@value #DEPARTMENT_NO_CATALOG},
   * completo). {@link #clear(int)} también los borra.
   */
  public void loadEdgeCases(int year) {
    List<SampleCase> cases =
        List.of(
            new SampleCase(
                CASE_NOVELTY_AFTER_START,
                "Persona Ficticia 15",
                DEPARTMENT_ANTIOQUIA,
                ENTITY_MIXED,
                "1",
                1,
                1,
                1,
                NOVELTY_COMMON,
                null,
                true,
                true,
                false),
            new SampleCase(
                CASE_ENCODED_OTHER_CAUSE,
                "Persona Ficticia 16",
                DEPARTMENT_ANTIOQUIA,
                ENTITY_MIXED,
                "1",
                2,
                1,
                1,
                NOVELTY_OTHER_CAUSE,
                ENCODED_OTHER_CAUSE_TEXT,
                true,
                false,
                false),
            new SampleCase(
                CASE_DEPARTMENT_NO_CATALOG,
                "Persona Ficticia 17",
                DEPARTMENT_NO_CATALOG,
                ENTITY_MIXED,
                "1",
                1,
                1,
                1,
                null,
                null,
                true,
                true,
                true));
    for (SampleCase sampleCase : cases) {
      insertSample(year, sampleCase);
    }
  }

  /** Borra todo lo que creó {@link #load(int)}: muestra, capítulos, catálogos y usuarios. */
  public void clear(int year) {
    jdbc.update(
        "DELETE FROM EDIN_MUESTRA WHERE ANIO = ? AND C_C BETWEEN ? AND ?",
        String.valueOf(year),
        FIRST_CODE,
        LAST_CODE);
    // Por si una carga anterior fue de otro año: el rango de cédulas es solo del fixture.
    jdbc.update("DELETE FROM EDIN_MUESTRA WHERE C_C BETWEEN ? AND ?", FIRST_CODE, LAST_CODE);
    jdbc.update("DELETE FROM EDIN_20_CAP_B_UBICA WHERE C_C BETWEEN ? AND ?", FIRST_CODE, LAST_CODE);
    jdbc.update(
        "DELETE FROM EDIN_20_CAP_H_PREV_PRACT WHERE C_C BETWEEN ? AND ?", FIRST_CODE, LAST_CODE);
    jdbc.update("DELETE FROM GRAL_ENTI WHERE COD_ENT BETWEEN 900001 AND 900099");
    jdbc.update("DELETE FROM GRAL_DEPARTAMENTOS WHERE COD_DEPTO IN (5, 8, 76)");
    jdbc.update(
        "DELETE FROM GRAL_NOVEDAD WHERE IDFNOV IN (?, ?)", NOVELTY_COMMON, NOVELTY_OTHER_CAUSE);
    for (String username : USERNAMES) {
      jdbc.update("DELETE FROM GRAL_TERRITORIAL_ACCESO WHERE COD_USUA = ?", username);
      jdbc.update("DELETE FROM GRAL_USUARIOS_ADM WHERE COD_USUA = ?", username);
    }
  }

  private void insertCatalogs() {
    insertDepartment(DEPARTMENT_ANTIOQUIA, "DEPARTMENT_ANTIOQUIA (ficticio)");
    insertDepartment(DEPARTMENT_ATLANTICO, "DEPARTMENT_ATLANTICO (ficticio)");
    insertDepartment(DEPARTMENT_VALLE, "DEPARTMENT_VALLE (ficticio)");
    insertEntity(ENTITY_MIXED, "Entidad Ficticia Mixta");
    insertEntity(ENTITY_EDID, "Entidad Ficticia EDID");
    insertEntity(ENTITY_AT_SIGN, "@Ficticia");
    // ENTITY_NO_CATALOG no se inserta a propósito.
    jdbc.update(
        "INSERT INTO GRAL_NOVEDAD (PROY, CONSEC, IDFNOV, NOVEDAD, ANIO) VALUES (?, ?, ?, ?, ?)",
        "EDIN",
        1,
        NOVELTY_COMMON,
        "Retiro ficticio",
        2026);
    jdbc.update(
        "INSERT INTO GRAL_NOVEDAD (PROY, CONSEC, IDFNOV, NOVEDAD, ANIO) VALUES (?, ?, ?, ?, ?)",
        "EDIN",
        2,
        NOVELTY_OTHER_CAUSE,
        "Otra causa",
        2026);
  }

  private void insertDepartment(int code, String name) {
    jdbc.update(
        "INSERT INTO GRAL_DEPARTAMENTOS (COD_DEPTO, NOM_DEPTO, TERRIT) VALUES (?, ?, ?)",
        code,
        name,
        "Territorial Ficticia " + code);
  }

  private void insertEntity(int code, String name) {
    jdbc.update(
        "INSERT INTO GRAL_ENTI (PROY, COD_ENT, ENTIDAD, ACT_ENT) VALUES ('EDIN', ?, ?, 1)",
        code,
        name);
  }

  /**
   * COD_USUA de GRAL_TERRITORIAL_ACCESO guarda aquí el NOMBRE de usuario (en la base real es el id
   * numérico): el esquema de desarrollo identifica a los administradores por su nombre.
   */
  private void insertUsers() {
    insertUser(USER_TERRITORIAL_MULTI, 2, "5");
    insertUser(USER_TERRITORIAL_SINGLE, 2, "76");
    insertUser(USER_OPERATIVE, 3, null);
    insertUser(USER_ENGINEER, 1, null);
    insertAccess(900001, DEPARTMENT_ANTIOQUIA, USER_TERRITORIAL_MULTI);
    insertAccess(900002, DEPARTMENT_ATLANTICO, USER_TERRITORIAL_MULTI);
  }

  private void insertUser(String username, int role, String departmentCode) {
    jdbc.update(
        "INSERT INTO GRAL_USUARIOS_ADM (COD_USUA, CLAVE, NOMBRE, CARGO, ROL, COD_DPTO, PROY)"
            + " VALUES (?, ?, ?, 'Ficticio', ?, ?, 'EDIN')",
        username,
        USER_PASSWORD,
        "Usuario Ficticio " + username,
        role,
        departmentCode);
  }

  private void insertAccess(int id, int department, String username) {
    jdbc.update(
        "INSERT INTO GRAL_TERRITORIAL_ACCESO (CODIGO, COD_DEPTO, COD_USUA, PROY)"
            + " VALUES (?, ?, ?, 'EDIN, EDID')",
        id,
        department,
        username);
  }

  private void insertSample(int year, SampleCase sampleCase) {
    int number = Integer.parseInt(sampleCase.code().substring(sampleCase.code().length() - 2));
    String suffix = String.format("%02d", number);
    jdbc.update(
        "INSERT INTO EDIN_MUESTRA (ANIO, COD_ENT, COD_DEPTO, C_C, NOM_APE, ATV, TC1, LGN, PSW,"
            + " ENC, AREA_TRA, TC2, IDFNOV, NOV_OTRA_CAUSA, IDMUES)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        String.valueOf(year),
        String.valueOf(sampleCase.entity()),
        String.valueOf(sampleCase.department()),
        sampleCase.code(),
        sampleCase.name(),
        sampleCase.active(),
        sampleCase.channel(),
        sampleCase.hasKey() ? "FX0000" + suffix : null,
        sampleCase.hasKey() ? "Fx#000" + suffix : null,
        sampleCase.survey(),
        sampleCase.level(),
        9000000L + number,
        sampleCase.novelty(),
        sampleCase.otherCause(),
        "IDM" + year + "F" + suffix);
    LocalDateTime saved = LocalDateTime.of(year, 3, 10, 9, number);
    if (sampleCase.startedB()) {
      jdbc.update(
          "INSERT INTO EDIN_20_CAP_B_UBICA (C_C, ESTADO, TC, FECHA, ANIO, B07)"
              + " VALUES (?, 1, ?, ?, ?, '1')",
          sampleCase.code(),
          sampleCase.channel(),
          Timestamp.valueOf(saved),
          String.valueOf(year));
    }
    if (sampleCase.finishedH()) {
      jdbc.update(
          "INSERT INTO EDIN_20_CAP_H_PREV_PRACT (C_C, ESTADO, TC, FECHA, H06)"
              + " VALUES (?, 1, ?, ?, '1')",
          sampleCase.code(),
          sampleCase.channel(),
          Timestamp.valueOf(saved.plusHours(1)));
    }
  }
}
