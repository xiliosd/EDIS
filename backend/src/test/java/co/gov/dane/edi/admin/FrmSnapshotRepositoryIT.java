package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.support.OracleIntegrationTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FrmSnapshotRepositoryIT extends OracleIntegrationTest {

  @Autowired private FrmSnapshotRepository snapshots;

  @Test
  void snapshotOfDevYearCoversTheClosure() {
    FrmSnapshot snapshot = snapshots.snapshot("EDIN", 2026);

    assertThat(snapshot.project()).isEqualTo("EDIN");
    assertThat(snapshot.year()).isEqualTo(2026);
    assertThat(snapshot.rows().keySet()).containsExactlyInAnyOrderElementsOf(FrmSnapshot.TABLES);
    assertThat(countsOf(snapshot))
        .containsExactly(
            Map.entry(FrmTable.PREGUNTA, 14),
            Map.entry(FrmTable.PRE_GRUPO, 14),
            Map.entry(FrmTable.CAP_PROY, 14),
            Map.entry(FrmTable.CAP_PERIODO, 14),
            Map.entry(FrmTable.ENCABEZADO, 2),
            Map.entry(FrmTable.PRE_AYUDA, 9),
            Map.entry(FrmTable.OBJ_PRE, 13),
            Map.entry(FrmTable.ENC_AYUDA, 1));
    // El periodo 5 (2025) y el encabezado 3 (2025) comparten el cap_proy 101 pero no son del año.
    assertThat(ids(snapshot, FrmTable.CAP_PERIODO)).doesNotContain(new BigDecimal(5));
    assertThat(ids(snapshot, FrmTable.ENCABEZADO))
        .containsExactly(new BigDecimal(1), new BigDecimal(2));
    for (FrmTable table : FrmSnapshot.TABLES) {
      List<BigDecimal> ids = ids(snapshot, table);
      List<BigDecimal> sorted = new ArrayList<>(ids);
      Collections.sort(sorted);
      assertThat(ids).as(table.tableName()).isEqualTo(sorted);
      for (Map<String, Object> row : snapshot.rows().get(table)) {
        assertThat(row.keySet()).containsExactlyElementsOf(table.columns());
        assertThat(row.values())
            .allMatch(
                value -> value == null || value instanceof String || value instanceof BigDecimal);
      }
    }
    Map<String, Object> question = snapshot.rows().get(FrmTable.PREGUNTA).get(0);
    assertThat(question.get("PREGUNTA"))
        .isEqualTo("Nivel del empleo que desempe&ntilde;a en la entidad durante el 200X");
  }

  @Test
  void hashesAreStableAcrossReads() throws Exception {
    FrmSnapshot first = snapshots.snapshot("EDIN", 2026);
    FrmSnapshot second = snapshots.snapshot("EDIN", 2026);

    assertThat(second).isEqualTo(first);
    for (FrmTable table : FrmSnapshot.TABLES) {
      List<String> hashes = hashes(first, table);
      assertThat(hashes(second, table)).isEqualTo(hashes);
      List<String> reversed = new ArrayList<>(hashes);
      Collections.reverse(reversed);
      assertThat(FrmSnapshot.tableSum(reversed)).isEqualTo(FrmSnapshot.tableSum(hashes));
    }
    Map<String, Object> group = first.rows().get(FrmTable.OBJ_PRE).get(0);
    String canonical =
        "COD_OBJ_PRE=1\u001FCOD_GRUPO=10\u001FGRUPO=10\u001FCOD_OBJ=1\u001FCOD_CAP_PROY=102";
    assertThat(FrmSnapshot.rowHash(FrmTable.OBJ_PRE, group)).isEqualTo(sha256(canonical));
    // El hash de "b" (3e23…) va antes que el de "a" (ca97…) al ordenar.
    assertThat(FrmSnapshot.tableSum(List.of(sha256("a"), sha256("b"))))
        .isEqualTo(sha256(sha256("b") + "\n" + sha256("a")));
    Map<String, Object> withNull = new LinkedHashMap<>();
    withNull.put("COD_AYU", new BigDecimal("1.00"));
    withNull.put("COD_ENC", new BigDecimal(3));
    withNull.put("TEXTO", null);
    assertThat(FrmSnapshot.rowHash(FrmTable.ENC_AYUDA, withNull))
        .isEqualTo(sha256("COD_AYU=1\u001FCOD_ENC=3\u001FTEXTO=\u0000"));
  }

  private static Map<FrmTable, Integer> countsOf(FrmSnapshot snapshot) {
    Map<FrmTable, Integer> counts = new LinkedHashMap<>();
    FrmSnapshot.TABLES.forEach(table -> counts.put(table, snapshot.rows().get(table).size()));
    return counts;
  }

  private static List<BigDecimal> ids(FrmSnapshot snapshot, FrmTable table) {
    return snapshot.rows().get(table).stream()
        .map(row -> (BigDecimal) row.get(table.idColumn()))
        .toList();
  }

  private static List<String> hashes(FrmSnapshot snapshot, FrmTable table) {
    return snapshot.rows().get(table).stream().map(row -> FrmSnapshot.rowHash(table, row)).toList();
  }

  private static String sha256(String text) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
  }
}
