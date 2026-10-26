package co.gov.dane.edi;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Garantiza que el cuestionario ficticio cubre las 9 páginas que recorre la encuesta. */
class DevDataIT extends OracleIntegrationTest {

  @Autowired private NamedParameterJdbcTemplate jdbc;

  @Test
  void everyPageHasAtLeastOneQuestionForTheActiveYear() {
    List<String> pages =
        jdbc.queryForList(
            "SELECT DISTINCT PP.CAPITULO FROM FRM_CAP_PROY PP JOIN FRM_CAP_PERIODO PER"
                + " ON PER.COD_CAP_PROY = PP.COD_CAP_PROY WHERE PER.ANIO = '2026'"
                + " ORDER BY PP.CAPITULO",
            Map.of(),
            String.class);
    assertThat(pages).containsExactly("B1", "C1", "C2", "D1", "D2", "E1", "F1", "G1", "H1");
  }

  @Test
  void answerTablesStartEmpty() {
    for (String table :
        List.of(
            "EDIN_20_CAP_B_UBICA",
            "EDIN_20_CAP_C_EXP_PERS",
            "EDIN_20_CAP_D_GEST_TAL",
            "EDIN_20_CAP_E_USO_TECN",
            "EDIN_20_CAP_F_GEST_ENT",
            "EDIN_20_CAP_G_REN_CUEN",
            "EDIN_20_CAP_H_PREV_PRACT")) {
      Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Map.of(), Integer.class);
      assertThat(rows).as(table).isZero();
    }
  }

  @Test
  void flowRespondentsExist() {
    assertThat(
            jdbc.queryForList(
                "SELECT LGN FROM EDIN_MUESTRA WHERE LGN IN ('PRUEBA05','PRUEBA06') ORDER BY LGN",
                Map.of(),
                String.class))
        .containsExactly("PRUEBA05", "PRUEBA06");
  }

  private Set<String> columnsOf(String table) {
    return Set.copyOf(
        jdbc.queryForList(
            "SELECT COLUMN_NAME FROM ALL_TAB_COLUMNS WHERE TABLE_NAME = :table"
                + " AND OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')",
            Map.of("table", table),
            String.class));
  }

  private List<String> primaryKeyOf(String table) {
    return jdbc.queryForList(
        "SELECT CC.COLUMN_NAME FROM ALL_CONSTRAINTS C JOIN ALL_CONS_COLUMNS CC"
            + " ON CC.OWNER = C.OWNER AND CC.CONSTRAINT_NAME = C.CONSTRAINT_NAME"
            + " WHERE C.CONSTRAINT_TYPE = 'P' AND C.TABLE_NAME = :table"
            + " AND C.OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') ORDER BY CC.POSITION",
        Map.of("table", table),
        String.class);
  }

  @Test
  void frmTablesHaveRealColumnsAndKeys() {
    Map<String, List<String>> expected =
        Map.ofEntries(
            Map.entry("FRM_PREGUNTA", List.of("COD_PRE", "PREGUNTA", "TIPO")),
            Map.entry("FRM_PRE_GRUPO", List.of("COD_PRE_GRUPO", "GRUPO", "ORDEN", "COD_PRE")),
            Map.entry(
                "FRM_CAP_PROY",
                List.of("COD_CAP_PROY", "CAPITULO", "NUMERAL", "COD_PRE_GRUPO", "PROY", "ANIO")),
            Map.entry(
                "FRM_CAP_PERIODO",
                List.of("ID_PERIODO", "ANIO", "PROY", "COD_CAP_PROY", "NUMERAL")),
            Map.entry(
                "FRM_OBJ_PRE",
                List.of("COD_OBJ_PRE", "COD_GRUPO", "GRUPO", "COD_OBJ", "COD_CAP_PROY")),
            Map.entry("FRM_SALTO", List.of("COD_SAL", "COD_CAP_PROY", "SALTO", "VALOR_SALTO")),
            Map.entry("FRM_RTA_GRUPO", List.of("COD_GRUPO", "GRUPO", "ORDEN", "VALOR", "COD_RTA")),
            Map.entry(
                "FRM_ENCABEZADO", List.of("COD_ENC", "COD_CAP_PROY", "TIPO", "ENCABEZADO", "ANIO")),
            Map.entry("FRM_PRE_AYUDA", List.of("COD_AYU", "COD_CAP_PROY", "TEXTO")),
            Map.entry("FRM_ENC_AYUDA", List.of("COD_AYU", "COD_ENC", "TEXTO")));
    Map<String, String> primaryKeys =
        Map.ofEntries(
            Map.entry("FRM_PREGUNTA", "COD_PRE"),
            Map.entry("FRM_PRE_GRUPO", "COD_PRE_GRUPO"),
            Map.entry("FRM_CAP_PROY", "COD_CAP_PROY"),
            Map.entry("FRM_CAP_PERIODO", "ID_PERIODO"),
            Map.entry("FRM_OBJ_PRE", "COD_OBJ_PRE"),
            Map.entry("FRM_SALTO", "COD_SAL"),
            Map.entry("FRM_RTA_GRUPO", "COD_GRUPO"),
            Map.entry("FRM_ENCABEZADO", "COD_ENC"),
            Map.entry("FRM_PRE_AYUDA", "COD_AYU"),
            Map.entry("FRM_ENC_AYUDA", "COD_AYU"));
    expected.forEach(
        (table, columns) -> {
          assertThat(columnsOf(table))
              .as("columnas de " + table)
              .containsExactlyInAnyOrderElementsOf(columns);
          assertThat(primaryKeyOf(table))
              .as("clave primaria de " + table)
              .containsExactly(primaryKeys.get(table));
        });
  }

  @Test
  void everyPageHasOneHelpRow() {
    Integer pagesWithHelp =
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT PP.CAPITULO) FROM FRM_PRE_AYUDA A JOIN FRM_CAP_PROY PP"
                + " ON PP.COD_CAP_PROY = A.COD_CAP_PROY",
            Map.of(),
            Integer.class);
    assertThat(pagesWithHelp).isEqualTo(9);
  }

  @Test
  void pageCodeRowsCarryProjectYearAndNumeral() {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT PROY, ANIO, NUMERAL FROM FRM_CAP_PROY WHERE COD_CAP_PROY IN (102, 301)"
                + " ORDER BY COD_CAP_PROY",
            Map.of());
    assertThat(rows).extracting(row -> row.get("PROY")).containsExactly("EDIN", "EDIN");
    assertThat(rows).extracting(row -> row.get("ANIO")).containsExactly("2026", "2026");
    assertThat(rows).extracting(row -> row.get("NUMERAL")).containsExactly("B02*.", "C01A.");
  }

  @Test
  void respondentFlowDataStillLoads() {
    assertThat(pendingNumerals("B1")).containsExactly("B2.", "B02*.", "B06*.", "B99*.");
    assertThat(pendingNumerals("C1")).containsExactly("C01A.", "C01B.");
    assertThat(pendingNumerals("D1")).containsExactly("D01A.", "D05*.");
    for (String page : List.of("C2", "D2", "E1", "F1", "G1", "H1")) {
      assertThat(pendingNumerals(page)).as(page).hasSize(1);
    }
  }

  private List<String> pendingNumerals(String page) {
    return jdbc.queryForList(
        "SELECT PER.NUMERAL FROM FRM_CAP_PROY PP"
            + " JOIN FRM_PRE_GRUPO G ON G.COD_PRE_GRUPO = PP.COD_PRE_GRUPO"
            + " JOIN FRM_CAP_PERIODO PER ON PER.COD_CAP_PROY = PP.COD_CAP_PROY"
            + " AND PER.PROY = 'EDIN' AND PER.ANIO = '2026'"
            + " WHERE PP.CAPITULO = :page ORDER BY G.GRUPO, G.ORDEN",
        Map.of("page", page),
        String.class);
  }
}
