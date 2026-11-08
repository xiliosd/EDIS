package co.gov.dane.edi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Comprueba que la muestra ficticia cubre cada caso de la Fase 4 y que se limpia por completo. */
class OperationalSampleFixtureIT extends OracleIntegrationTest {

  private static final int YEAR = 2026;
  private static final String FIXTURE_RANGE = " C_C BETWEEN '900000001' AND '900000099'";

  @Autowired private JdbcTemplate jdbc;

  private OperationalSampleFixture fixture;

  @BeforeEach
  void loadFixture() {
    fixture = new OperationalSampleFixture(jdbc);
    fixture.clear(YEAR);
    fixture.load(YEAR);
  }

  @AfterEach
  void clearFixture() {
    fixture.clear(YEAR);
  }

  private int count(String where) {
    Integer rows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM EDIN_MUESTRA WHERE ANIO = '" + YEAR + "' AND " + where,
            Integer.class);
    return rows == null ? 0 : rows;
  }

  @Test
  void everyCaseHasItsRow() {
    assertThat(count("C_C = '" + OperationalSampleFixture.CASE_COMPLETE + "'")).isEqualTo(1);
    // Completo con novedad: prevalece completo (B07 y H06) aunque tenga IDFNOV.
    assertThat(
            count(
                "C_C = '"
                    + OperationalSampleFixture.CASE_COMPLETE_WITH_NOVELTY
                    + "' AND IDFNOV = 3"))
        .isEqualTo(1);
    assertThat(count("C_C = '" + OperationalSampleFixture.CASE_INCOMPLETE + "'")).isEqualTo(1);
    assertThat(count("C_C = '" + OperationalSampleFixture.CASE_NOT_STARTED + "'")).isEqualTo(1);
    assertThat(
            count(
                "C_C = '" + OperationalSampleFixture.CASE_NOVELTY_NOT_STARTED + "' AND IDFNOV = 3"))
        .isEqualTo(1);
    assertThat(
            count(
                "C_C = '"
                    + OperationalSampleFixture.CASE_NOVELTY_OTHER_CAUSE
                    + "' AND IDFNOV = 8 AND NOV_OTRA_CAUSA IS NOT NULL"))
        .isEqualTo(1);
  }

  @Test
  void sampleCoversChannelsSurveysAndStatus() {
    assertThat(
            count(
                "COD_ENT = '"
                    + OperationalSampleFixture.ENTITY_MIXED
                    + "' AND TC1 = 2 AND ATV = 1"))
        .isPositive();
    assertThat(
            count(
                "COD_ENT = '"
                    + OperationalSampleFixture.ENTITY_MIXED
                    + "' AND TC1 = 1 AND ATV = 1"))
        .isPositive();
    assertThat(count("ENC = '2' AND" + FIXTURE_RANGE)).isPositive();
    assertThat(count("ATV = 0 AND" + FIXTURE_RANGE)).isEqualTo(1);
    assertThat(count("COD_ENT = '" + OperationalSampleFixture.ENTITY_NO_CATALOG + "'"))
        .isPositive();
    assertThat(count("LGN IS NULL AND TC1 = 1 AND ATV = 1 AND" + FIXTURE_RANGE)).isPositive();
    assertThat(count("LGN IS NULL AND TC1 = 2 AND ATV = 1 AND" + FIXTURE_RANGE)).isPositive();
    for (int level = 1; level <= 3; level++) {
      assertThat(count("AREA_TRA = " + level + " AND" + FIXTURE_RANGE)).isPositive();
    }
  }

  @Test
  void sampleHasTheHostileNames() {
    assertThat(count("NOM_APE = '=SUMA(1;2)'")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM GRAL_ENTI WHERE ENTIDAD = '@Ficticia' AND COD_ENT = "
                    + OperationalSampleFixture.ENTITY_AT_SIGN,
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void chaptersAndCatalogsMatchTheCases() {
    List<String> complete =
        jdbc.queryForList(
            "SELECT M.C_C FROM EDIN_MUESTRA M"
                + " JOIN EDIN_20_CAP_B_UBICA B ON B.C_C = M.C_C AND B.B07 IS NOT NULL"
                + " JOIN EDIN_20_CAP_H_PREV_PRACT H ON H.C_C = M.C_C AND H.H06 IS NOT NULL"
                + " WHERE M.ATV = 1 AND M.C_C BETWEEN '900000001' AND '900000099'",
            String.class);
    assertThat(complete)
        .contains(
            OperationalSampleFixture.CASE_COMPLETE,
            OperationalSampleFixture.CASE_COMPLETE_WITH_NOVELTY)
        .doesNotContain(
            OperationalSampleFixture.CASE_INCOMPLETE, OperationalSampleFixture.CASE_NOT_STARTED);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM GRAL_ENTI WHERE COD_ENT = "
                    + OperationalSampleFixture.ENTITY_NO_CATALOG,
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForList(
                "SELECT COD_DEPTO FROM GRAL_DEPARTAMENTOS WHERE COD_DEPTO IN (5, 8, 76)"
                    + " ORDER BY COD_DEPTO",
                Integer.class))
        .containsExactly(5, 8, 76);
    assertThat(
            jdbc.queryForList(
                "SELECT COD_DEPTO FROM GRAL_TERRITORIAL_ACCESO WHERE COD_USUA = ?"
                    + " AND PROY LIKE '%EDIN%' ORDER BY COD_DEPTO",
                Integer.class, OperationalSampleFixture.USER_TERRITORIAL_MULTI))
        .containsExactly(5, 8);
    Map<String, Object> single =
        jdbc.queryForMap(
            "SELECT ROL, COD_DPTO FROM GRAL_USUARIOS_ADM WHERE COD_USUA = ?",
            OperationalSampleFixture.USER_TERRITORIAL_SINGLE);
    assertThat(((Number) single.get("ROL")).intValue()).isEqualTo(2);
    assertThat(single.get("COD_DPTO")).isEqualTo("76");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM GRAL_TERRITORIAL_ACCESO WHERE COD_USUA = ?",
                Integer.class,
                OperationalSampleFixture.USER_TERRITORIAL_SINGLE))
        .isZero();
    assertThat(
            jdbc.queryForList(
                "SELECT ROL FROM GRAL_USUARIOS_ADM WHERE COD_USUA IN (?, ?) ORDER BY COD_USUA",
                Integer.class,
                OperationalSampleFixture.USER_ENGINEER,
                OperationalSampleFixture.USER_OPERATIVE))
        .containsExactly(1, 3);
  }

  @Test
  void loadTwiceDoesNotDuplicateAndClearLeavesNothing() {
    fixture.load(YEAR);
    assertThat(count(FIXTURE_RANGE)).isEqualTo(OperationalSampleFixture.CASE_COUNT);
    fixture.clear(YEAR);
    assertThat(count(FIXTURE_RANGE)).isZero();
    for (String table : List.of("EDIN_20_CAP_B_UBICA", "EDIN_20_CAP_H_PREV_PRACT")) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM " + table + " WHERE" + FIXTURE_RANGE, Integer.class))
          .as(table)
          .isZero();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM GRAL_ENTI WHERE COD_ENT BETWEEN 900001 AND 900099",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM GRAL_USUARIOS_ADM WHERE COD_USUA IN"
                    + " ('territorial-multi','territorial-solo','operativo','ingeniero')",
                Integer.class))
        .isZero();
  }
}
