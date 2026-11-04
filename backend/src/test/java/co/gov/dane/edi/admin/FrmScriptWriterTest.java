package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrmScriptWriterTest {

  private final FrmScriptWriter writer = new FrmScriptWriter();

  @Test
  void escapesQuotesAmpersandsAndNewlines() {
    FrmSnapshot snapshot =
        snapshotWith(FrmTable.PREGUNTA, row(FrmTable.PREGUNTA, 7, "O'Brien & <b>\nX", "L"));

    String script = writer.write(snapshot, PackageKind.ANIO);

    assertThat(script).startsWith("SET DEFINE OFF\nWHENEVER SQLERROR EXIT ROLLBACK\n");
    assertThat(script).contains("'O''Brien & <b>' || CHR(10) || 'X'");
    assertThat(script).doesNotContain("O'Brien");
    assertThat(script).endsWith("COMMIT;\n");
    // Ningún literal parte una línea: una línea "/" o "." dentro de un texto cortaría el bloque.
    assertThat(script.lines()).noneMatch(line -> line.equals("X'"));
  }

  @Test
  void yearPackageRaisesOnDifferentRowCorrectionUpdates() {
    FrmSnapshot snapshot =
        snapshotWith(
            FrmTable.ENCABEZADO, row(FrmTable.ENCABEZADO, 5, 101, "E", "Texto corregido", "2027"));

    String year = writer.write(snapshot, PackageKind.ANIO);
    String correction = writer.write(snapshot, PackageKind.CORRECCION);

    String raise = "RAISE_APPLICATION_ERROR(-20001, 'Id ocupado: FRM_ENCABEZADO COD_ENC=5')";
    assertThat(year).contains("INSERT INTO FRM_ENCABEZADO (COD_ENC, COD_CAP_PROY, TIPO,");
    assertThat(year).contains(raise).doesNotContain("UPDATE");
    // Una corrección que difiere solo en el texto lo actualiza; en otra columna aborta igual.
    assertThat(correction).contains(raise);
    assertThat(correction).contains("UPDATE FRM_ENCABEZADO SET ENCABEZADO = 'Texto corregido'");
    assertThat(correction).doesNotContain("SET TIPO").doesNotContain("SET COD_CAP_PROY");
    assertThat(correction).doesNotContain("TIPO = 'E',");
  }

  @Test
  void correctionScriptUpdatesOnlyCorrectableColumns() {
    FrmSnapshot snapshot =
        snapshotWith(
            FrmTable.CAP_PROY, row(FrmTable.CAP_PROY, 9, "H1", "H01*.", 42, "EDIN", "2027"));
    FrmSnapshot periods =
        snapshotWith(FrmTable.CAP_PERIODO, row(FrmTable.CAP_PERIODO, 3, 2027, "EDIN", 9, "H01*."));

    String correction = writer.write(snapshot, PackageKind.CORRECCION);

    assertThat(correction).contains("UPDATE FRM_CAP_PROY SET COD_PRE_GRUPO = 42");
    assertThat(correction.lines().filter(line -> line.contains("UPDATE"))).hasSize(1);
    // Una tabla sin columnas corregibles nunca se actualiza, ni en una corrección.
    assertThat(writer.write(periods, PackageKind.CORRECCION)).doesNotContain("UPDATE");
  }

  @Test
  void rendersNullsNumbersTimestampsAndDependencyOrder() {
    Map<FrmTable, List<Map<String, Object>>> rows = emptyRows();
    rows.put(FrmTable.ENC_AYUDA, List.of(row(FrmTable.ENC_AYUDA, 1, 3, null)));
    rows.put(
        FrmTable.PREGUNTA, List.of(row(FrmTable.PREGUNTA, new BigDecimal("10.000"), "P", "L")));
    FrmSnapshot snapshot = new FrmSnapshot("EDIN", 2027, rows);

    String script = writer.write(snapshot, PackageKind.ANIO);

    assertThat(script).contains("WHERE COD_PRE = 10;").contains("AND TEXTO IS NULL");
    assertThat(script).contains("VALUES (1, 3, NULL)");
    assertThat(script.indexOf("FRM_PREGUNTA")).isLessThan(script.indexOf("INSERT INTO FRM_ENC"));
    assertThat(script).contains("-- Encuesta: EDIN").contains("-- Año: 2027");
    assertThat(script).contains("FRM_PREGUNTA: 1").contains("FRM_ENC_AYUDA: 1");
    assertThat(script).contains("FRM_CAP_PERIODO: 0").contains("sqlplus");
    assertThat(FrmScriptWriter.literal(LocalDateTime.of(2027, 1, 2, 3, 4, 5)))
        .isEqualTo("TIMESTAMP '2027-01-02 03:04:05'");
  }

  @Test
  void splitsLongLiteralsAndRejectsOversizedText() {
    String longText = "a".repeat(1000) + "'" + "b".repeat(1500);

    String literal = FrmScriptWriter.literal(longText);

    assertThat(literal.lines()).hasSize(3).allMatch(line -> line.length() <= 1010);
    assertThat(literal).contains("'\n|| '");
    assertThat(literal.replace("'\n|| '", "")).isEqualTo("'" + longText.replace("'", "''") + "'");
    String tooLong = "ñ".repeat(2001);
    assertThatThrownBy(() -> FrmScriptWriter.literal(tooLong))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("4000");
  }

  @Test
  void rejectsAProjectCodeThatCouldBreakTheHeader() {
    FrmSnapshot snapshot = new FrmSnapshot("EDIN\nDROP", 2027, emptyRows());

    assertThatThrownBy(() -> writer.write(snapshot, PackageKind.ANIO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static FrmSnapshot snapshotWith(FrmTable table, Map<String, Object> row) {
    Map<FrmTable, List<Map<String, Object>>> rows = emptyRows();
    rows.put(table, List.of(row));
    return new FrmSnapshot("EDIN", 2027, rows);
  }

  private static Map<FrmTable, List<Map<String, Object>>> emptyRows() {
    Map<FrmTable, List<Map<String, Object>>> rows = new EnumMap<>(FrmTable.class);
    FrmSnapshot.TABLES.forEach(table -> rows.put(table, List.of()));
    return rows;
  }

  private static Map<String, Object> row(FrmTable table, Object... values) {
    Map<String, Object> row = new LinkedHashMap<>();
    List<String> columns = table.columns();
    Object[] normalized =
        Arrays.stream(values)
            .map(value -> value instanceof Integer number ? BigDecimal.valueOf(number) : value)
            .toArray();
    for (int index = 0; index < columns.size(); index++) {
      row.put(columns.get(index), normalized[index]);
    }
    return row;
  }
}
