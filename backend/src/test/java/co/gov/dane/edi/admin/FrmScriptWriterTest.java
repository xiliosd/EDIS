package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.support.HostileTexts;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FrmScriptWriterTest {

  private static final Pattern ASCII_PART = Pattern.compile("'((?:[^']|'')*)'");
  private static final Pattern CHR_PART = Pattern.compile("CHR\\((\\d+)\\)");
  private static final Pattern UNISTR_PART =
      Pattern.compile("TO_CHAR\\(UNISTR\\('((?:\\\\[0-9A-F]{4})+)'\\)\\)");

  private static final String SOURCE_CHARSET = "AL32UTF8";

  private final FrmScriptWriter writer = new FrmScriptWriter();

  @Test
  void escapesQuotesAmpersandsAndNewlines() {
    FrmSnapshot snapshot =
        snapshotWith(FrmTable.PREGUNTA, row(FrmTable.PREGUNTA, 7, "O'Brien & <b>\nX", "L"));

    String script = writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET);

    assertThat(script)
        .startsWith(
            "SET DEFINE OFF\nSET AUTOCOMMIT OFF\nWHENEVER SQLERROR EXIT FAILURE ROLLBACK\n"
                + "WHENEVER OSERROR EXIT FAILURE ROLLBACK\n");
    assertThat(script).contains("  V_PREGUNTA := V_PREGUNTA || 'O''Brien & <b>';\n");
    assertThat(script).contains("  V_PREGUNTA := V_PREGUNTA || CHR(10);\n");
    assertThat(script).contains("  V_PREGUNTA := V_PREGUNTA || 'X';\n");
    assertThat(script).doesNotContain("O'Brien");
    assertThat(script).contains("sesion nueva");
    assertThat(script).endsWith("COMMIT;\n");
  }

  @Test
  void scriptIsPureAsciiWithBoundedLines() {
    List<Map<String, Object>> questions = new ArrayList<>();
    List<String> texts = HostileTexts.all();
    for (int index = 0; index < texts.size(); index++) {
      questions.add(row(FrmTable.PREGUNTA, index + 1, texts.get(index), "L"));
    }
    Map<FrmTable, List<Map<String, Object>>> rows = emptyRows();
    rows.put(FrmTable.PREGUNTA, questions);
    FrmSnapshot snapshot = new FrmSnapshot("EDIN", 2027, rows);

    for (PackageKind kind : PackageKind.values()) {
      byte[] bytes = writer.write(snapshot, kind, SOURCE_CHARSET).getBytes(StandardCharsets.UTF_8);
      int lineBytes = 0;
      for (byte value : bytes) {
        assertThat(value & 0xFF).as("byte no ASCII").isLessThan(0x80);
        lineBytes = value == '\n' ? 0 : lineBytes + 1;
        assertThat(lineBytes).as("bytes de una línea").isLessThanOrEqualTo(1000);
      }
    }
    // El emoji va entero en un mismo UNISTR: el corte cae entre puntos de código.
    assertThat(writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET))
        .contains("UNISTR('\\D83D\\DE00')");
  }

  @Test
  void textPartsDecodeBackToTheOriginalText() {
    for (String text : HostileTexts.all()) {
      List<String> parts = FrmScriptWriter.textParts(text);

      assertThat(decode(parts)).as(text).isEqualTo(text);
      assertThat(parts).allMatch(part -> part.length() <= 900);
    }
    assertThat(FrmScriptWriter.textParts("a\\b"))
        .containsExactly("'a'", "TO_CHAR(UNISTR('\\005C'))", "'b'");
    assertThat(FrmScriptWriter.textParts("\r\n\u0000\u000B\u007F"))
        .containsExactly("CHR(13)", "CHR(10)", "CHR(0)", "CHR(11)", "CHR(127)");
    assertThat(FrmScriptWriter.textParts("\u0085 ñ"))
        .containsExactly("TO_CHAR(UNISTR('\\0085\\2028\\00F1'))");
  }

  @Test
  void rejectsOversizedText() {
    String tooLong = "ñ".repeat(2001);

    assertThatThrownBy(() -> FrmScriptWriter.textParts(tooLong))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("4000");
  }

  @Test
  void yearPackageRaisesOnDifferentRowCorrectionUpdates() {
    FrmSnapshot snapshot =
        snapshotWith(
            FrmTable.ENCABEZADO, row(FrmTable.ENCABEZADO, 5, 101, "E", "Texto corregido", "2027"));

    String year = writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET);
    String correction = writer.write(snapshot, PackageKind.CORRECCION, SOURCE_CHARSET);

    String raise = "RAISE_APPLICATION_ERROR(-20001, 'Id ocupado: FRM_ENCABEZADO COD_ENC=5')";
    assertThat(year).contains("INSERT INTO FRM_ENCABEZADO (COD_ENC, COD_CAP_PROY, TIPO,");
    assertThat(year).contains("VALUES (5, V_COD_CAP_PROY, V_TIPO, V_ENCABEZADO, V_ANIO);");
    assertThat(year).contains("AND (ENCABEZADO = V_ENCABEZADO OR (ENCABEZADO IS NULL AND");
    assertThat(year).contains(raise).doesNotContain("UPDATE");
    // Una corrección que difiere solo en el texto lo actualiza; en otra columna aborta igual.
    assertThat(correction).contains(raise);
    assertThat(correction).contains("UPDATE FRM_ENCABEZADO SET ENCABEZADO = V_ENCABEZADO\n");
    assertThat(correction).contains("AND (TIPO = V_TIPO OR (TIPO IS NULL AND V_TIPO IS NULL))");
    assertThat(correction).doesNotContain("SET TIPO").doesNotContain("SET COD_CAP_PROY");
    assertThat(correction).doesNotContain("AND (ENCABEZADO = V_ENCABEZADO OR (ENCABEZADO IS NULL");
  }

  @Test
  void correctionScriptUpdatesOnlyCorrectableColumns() {
    FrmSnapshot snapshot =
        snapshotWith(
            FrmTable.CAP_PROY, row(FrmTable.CAP_PROY, 9, "H1", "H01*.", 42, "EDIN", "2027"));
    FrmSnapshot periods =
        snapshotWith(FrmTable.CAP_PERIODO, row(FrmTable.CAP_PERIODO, 3, 2027, "EDIN", 9, "H01*."));

    String correction = writer.write(snapshot, PackageKind.CORRECCION, SOURCE_CHARSET);

    assertThat(correction).contains("  V_COD_PRE_GRUPO NUMBER := 42;");
    assertThat(correction).contains("UPDATE FRM_CAP_PROY SET COD_PRE_GRUPO = V_COD_PRE_GRUPO");
    assertThat(correction.lines().filter(line -> line.contains("UPDATE"))).hasSize(1);
    // Una tabla sin columnas corregibles nunca se actualiza, ni en una corrección.
    assertThat(writer.write(periods, PackageKind.CORRECCION, SOURCE_CHARSET))
        .doesNotContain("UPDATE");
  }

  @Test
  void rendersNullsNumbersAndDependencyOrder() {
    Map<FrmTable, List<Map<String, Object>>> rows = emptyRows();
    rows.put(FrmTable.ENC_AYUDA, List.of(row(FrmTable.ENC_AYUDA, 1, 3, 501, null)));
    rows.put(
        FrmTable.PREGUNTA, List.of(row(FrmTable.PREGUNTA, new BigDecimal("10.000"), "P", "L")));
    FrmSnapshot snapshot = new FrmSnapshot("EDIN", 2027, rows);

    String script = writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET);

    assertThat(script).contains("WHERE COD_PRE = 10;").contains("  V_TEXTO VARCHAR2(4000 BYTE);\n");
    assertThat(script).doesNotContain("V_TEXTO := ");
    assertThat(script).contains("VALUES (1, V_COD_ENC, V_COD_AYU, V_TEXTO);");
    assertThat(script.indexOf("FRM_PREGUNTA")).isLessThan(script.indexOf("INSERT INTO FRM_ENC"));
    assertThat(script).contains("-- Encuesta: EDIN").contains("-- Anio: 2027");
    assertThat(script).contains("FRM_PREGUNTA: 1").contains("FRM_ENC_AYUDA: 1");
    assertThat(script).contains("FRM_CAP_PERIODO: 0").contains("sqlplus");
  }

  @Test
  void rejectsUnsupportedValueTypes() {
    Map<String, Object> question = row(FrmTable.PREGUNTA, 1, "Texto", "L");
    question.put("TIPO", java.time.LocalDateTime.of(2027, 1, 2, 3, 4, 5));
    FrmSnapshot snapshot = snapshotWith(FrmTable.PREGUNTA, question);

    assertThatThrownBy(() -> writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> FrmSnapshot.rowHash(FrmTable.PREGUNTA, question))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void headerNamesTheSourceCharacterSet() {
    FrmSnapshot snapshot = snapshotWith(FrmTable.PREGUNTA, row(FrmTable.PREGUNTA, 7, "Texto", "L"));

    String script = writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET);

    assertThat(script).contains("-- Juego de caracteres de origen: AL32UTF8\n");
    assertThat(script).doesNotContain("RAISE_APPLICATION_ERROR(-20002");
    assertThat(FrmScriptWriter.checksCharacterSet(script)).isFalse();
  }

  @Test
  void nonAsciiTextIsGuardedByACharacterSetCheckBeforeAnyInsert() {
    FrmSnapshot snapshot =
        snapshotWith(FrmTable.PRE_AYUDA, row(FrmTable.PRE_AYUDA, 9, 101, 501, "Atención"));

    String script = writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET);

    int check = script.indexOf("RAISE_APPLICATION_ERROR(-20002");
    assertThat(check).isPositive();
    assertThat(check).isLessThan(script.indexOf("INSERT INTO"));
    assertThat(script.indexOf("WHENEVER OSERROR")).isLessThan(check);
    assertThat(script)
        .contains("WHERE PARAMETER = 'NLS_CHARACTERSET'")
        .contains("IF TARGET_CHARACTER_SET <> 'AL32UTF8' THEN")
        .contains(
            "'Juego de caracteres distinto: origen AL32UTF8, destino ' || TARGET_CHARACTER_SET");
    assertThat(FrmScriptWriter.checksCharacterSet(script)).isTrue();
    assertThat(snapshot.hasNonAsciiText()).isTrue();
    FrmSnapshot ascii =
        snapshotWith(FrmTable.PRE_AYUDA, row(FrmTable.PRE_AYUDA, 9, 101, 501, "ASCII\n' \\ ~"));
    assertThat(ascii.hasNonAsciiText()).isFalse();
  }

  /** Un texto que trae el comentario del bloque no hace pasar el script por verificado. */
  @Test
  void characterSetCheckIsDetectedByAWholeLineNotByAText() {
    String comment = "-- Verificacion del juego de caracteres de destino";
    FrmSnapshot snapshot =
        snapshotWith(
            FrmTable.PRE_AYUDA,
            row(
                FrmTable.PRE_AYUDA,
                9,
                101,
                501,
                "x\n" + comment + "\nRAISE_APPLICATION_ERROR(-20002"));

    String script = writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET);

    assertThat(script).contains(comment);
    assertThat(FrmScriptWriter.checksCharacterSet(script)).isFalse();
  }

  @Test
  void helpDefinitionsAreCheckedBeforeAnyInsert() {
    Map<FrmTable, List<Map<String, Object>>> rows = emptyRows();
    rows.put(
        FrmTable.PRE_AYUDA,
        List.of(
            row(FrmTable.PRE_AYUDA, 9, 101, 503, "palabra"),
            row(FrmTable.PRE_AYUDA, 10, 102, 501, "otra"),
            row(FrmTable.PRE_AYUDA, 11, 103, 503, "repetida")));
    rows.put(FrmTable.ENC_AYUDA, List.of(row(FrmTable.ENC_AYUDA, 1, 3, 502, "encabezado")));
    FrmSnapshot snapshot = new FrmSnapshot("EDIN", 2027, rows);

    String script = writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET);

    assertThat(snapshot.helpDefinitionCodes()).containsExactly(501L, 502L, 503L);
    int check = script.indexOf("-- Verificacion de las definiciones de ayuda en GRAL_AYUDAS\n");
    assertThat(check).isPositive();
    assertThat(check).isLessThan(script.indexOf("INSERT INTO"));
    assertThat(script)
        .contains("SELECT COUNT(*) INTO DEFINITIONS FROM GRAL_AYUDAS WHERE COD = 501;")
        .contains("'Falta la definicion COD_AYU 503 en GRAL_AYUDAS'");
    assertThat(script.split("WHERE COD = 503;", -1)).hasSize(2);
    assertThat(script.lines()).allMatch(line -> line.chars().allMatch(c -> c < 0x80));
  }

  @Test
  void scriptWithoutHelpsDoesNotTouchHelpDefinitions() {
    FrmSnapshot snapshot = snapshotWith(FrmTable.PREGUNTA, row(FrmTable.PREGUNTA, 7, "Texto", "L"));

    assertThat(writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET))
        .doesNotContain("GRAL_AYUDAS WHERE");
  }

  /** La corrección solo actualiza la palabra enlazada; COD_AYU distinto aborta como id ocupado. */
  @Test
  void correctionOfAHelpUpdatesOnlyTheLinkedWord() {
    FrmSnapshot snapshot =
        snapshotWith(FrmTable.PRE_AYUDA, row(FrmTable.PRE_AYUDA, 9, 101, 501, "palabra"));

    String correction = writer.write(snapshot, PackageKind.CORRECCION, SOURCE_CHARSET);

    assertThat(correction).contains("UPDATE FRM_PRE_AYUDA SET TEXTO = V_TEXTO\n");
    assertThat(correction)
        .contains("AND (COD_AYU = V_COD_AYU OR (COD_AYU IS NULL AND V_COD_AYU IS NULL))");
  }

  @Test
  void rejectsACharacterSetNameThatCouldBreakTheScript() {
    FrmSnapshot snapshot = snapshotWith(FrmTable.PREGUNTA, row(FrmTable.PREGUNTA, 7, "Texto", "L"));

    for (String name : new String[] {null, "", "AL32UTF8'; DROP", "al32utf8", "A".repeat(31)}) {
      assertThatThrownBy(() -> writer.write(snapshot, PackageKind.ANIO, name))
          .as(String.valueOf(name))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsAProjectCodeThatCouldBreakTheHeader() {
    FrmSnapshot snapshot = new FrmSnapshot("EDIN\nDROP", 2027, emptyRows());

    assertThatThrownBy(() -> writer.write(snapshot, PackageKind.ANIO, SOURCE_CHARSET))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** Lo que haría Oracle con las partes: literales, CHR(n) y UNISTR('\XXXX...'). */
  private static String decode(List<String> parts) {
    StringBuilder text = new StringBuilder();
    for (String part : parts) {
      Matcher ascii = ASCII_PART.matcher(part);
      Matcher chr = CHR_PART.matcher(part);
      Matcher unistr = UNISTR_PART.matcher(part);
      if (ascii.matches()) {
        text.append(ascii.group(1).replace("''", "'"));
      } else if (chr.matches()) {
        text.append((char) Integer.parseInt(chr.group(1)));
      } else if (unistr.matches()) {
        for (String unit : unistr.group(1).substring(1).split("\\\\")) {
          text.append((char) Integer.parseInt(unit, 16));
        }
      } else {
        throw new AssertionError("Parte desconocida: " + part);
      }
    }
    return text.toString();
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
