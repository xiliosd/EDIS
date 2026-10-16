package co.gov.dane.edi;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.List;
import java.util.Map;
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
}
