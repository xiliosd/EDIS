package co.gov.dane.edi.admin;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Escribe el {@code frm.sql} idempotente de una fotografía FRM_*: el texto que la persona técnica
 * ejecuta en producción (la aplicación solo lee FRM_* allí). Cada fila es un bloque PL/SQL que
 * inserta la fila si su id está libre, no hace nada si ya está igual y aborta con ORA-20001 si el
 * id lo ocupa otra fila: con MAX+1 dos ambientes pueden haber dado el mismo id a filas distintas.
 *
 * <p>Un paquete de corrección además actualiza en una fila existente solo las columnas de {@link
 * FrmTable#correctableColumns()}; si difiere cualquier otra, aborta igual que un paquete de año.
 *
 * <p>El script es ASCII puro y ninguna línea pasa de 1000 bytes: SQL*Plus descarta sin error las
 * líneas largas (SP2-0027, que no dispara WHENEVER) y convierte el texto según el NLS_LANG del
 * cliente. Por eso cada texto se arma en una variable del bloque, una parte por línea: tramos ASCII
 * entre comillas, {@code CHR(n)} para los controles y {@code TO_CHAR(UNISTR('\XXXX'))} para lo
 * demás.
 */
@Component
public class FrmScriptWriter {

  /** Tope de VARCHAR2 con MAX_STRING_SIZE=STANDARD; el límite de 3a lo hace inalcanzable. */
  private static final int MAX_TEXT_BYTES = 4000;

  /** Largo máximo de una parte; con la asignación que la rodea la línea queda bajo 1000 bytes. */
  static final int MAX_PART_LENGTH = 900;

  private static final String UNISTR_OPEN = "TO_CHAR(UNISTR('";
  private static final String UNISTR_CLOSE = "'))";

  private static final Pattern PROJECT_CODE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");

  /** Nombre de un juego de caracteres de Oracle: entra en un literal y en un comentario. */
  private static final Pattern CHARACTER_SET = Pattern.compile("^[A-Z][A-Z0-9_]{0,29}$");

  /** Comentario que abre el bloque de verificación del juego de caracteres. */
  private static final String CHARACTER_SET_CHECK_MARK =
      "-- Verificacion del juego de caracteres de destino";

  /**
   * {@code sourceCharacterSet} es el NLS_CHARACTERSET de la base donde se genera: va en la cabecera
   * y, si hay texto no ASCII, en un primer bloque que aborta con ORA-20002 antes de escribir nada
   * cuando la base de destino tiene otro. {@code TO_CHAR(UNISTR())} reemplazaría en silencio con
   * "?" lo que el destino no puede guardar.
   */
  public String write(FrmSnapshot snapshot, PackageKind kind, String sourceCharacterSet) {
    if (!PROJECT_CODE.matcher(snapshot.project()).matches()) {
      throw new IllegalArgumentException("Código de encuesta no válido para el script");
    }
    if (sourceCharacterSet == null || !CHARACTER_SET.matcher(sourceCharacterSet).matches()) {
      throw new IllegalArgumentException("Juego de caracteres no válido para el script");
    }
    StringBuilder script = new StringBuilder();
    script.append("SET DEFINE OFF\n");
    script.append("SET AUTOCOMMIT OFF\n");
    script.append("WHENEVER SQLERROR EXIT FAILURE ROLLBACK\n");
    script.append("WHENEVER OSERROR EXIT FAILURE ROLLBACK\n");
    appendHeader(script, snapshot, kind, sourceCharacterSet);
    if (snapshot.hasNonAsciiText()) {
      appendCharacterSetCheck(script, sourceCharacterSet);
    }
    for (FrmTable table : FrmSnapshot.TABLES) {
      for (Map<String, Object> row : snapshot.rows().getOrDefault(table, List.of())) {
        appendRow(script, table, row, kind);
      }
    }
    script.append("COMMIT;\n");
    return script.toString();
  }

  /**
   * Si el script trae la verificación del juego de caracteres (tiene texto no ASCII). Se busca la
   * línea completa del comentario: un texto que lo contenga va dentro de una asignación.
   */
  public static boolean checksCharacterSet(String script) {
    return script.lines().anyMatch(CHARACTER_SET_CHECK_MARK::equals);
  }

  /** Primer bloque: nada se inserta si la base de destino no guarda los textos tal cual. */
  private static void appendCharacterSetCheck(StringBuilder script, String sourceCharacterSet) {
    script.append(CHARACTER_SET_CHECK_MARK).append('\n');
    script.append("DECLARE\n");
    script.append("  TARGET_CHARACTER_SET VARCHAR2(64);\n");
    script.append("BEGIN\n");
    script.append("  SELECT VALUE INTO TARGET_CHARACTER_SET FROM NLS_DATABASE_PARAMETERS\n");
    script.append("   WHERE PARAMETER = 'NLS_CHARACTERSET';\n");
    script.append("  IF TARGET_CHARACTER_SET <> '").append(sourceCharacterSet).append("' THEN\n");
    script
        .append("    RAISE_APPLICATION_ERROR(-20002,\n")
        .append("      'Juego de caracteres distinto: origen ")
        .append(sourceCharacterSet)
        .append(", destino ' || TARGET_CHARACTER_SET);\n");
    script.append("  END IF;\n");
    script.append("END;\n");
    script.append("/\n");
  }

  private static void appendHeader(
      StringBuilder script, FrmSnapshot snapshot, PackageKind kind, String sourceCharacterSet) {
    // La cabecera también es ASCII: sin tildes, para no depender del NLS_LANG del cliente.
    script.append("-- frm.sql generado por el administrador del cuestionario.\n");
    script.append("-- Encuesta: ").append(snapshot.project()).append('\n');
    script.append("-- Anio: ").append(snapshot.year()).append('\n');
    script.append("-- Paquete: ").append(kind.jsonValue()).append('\n');
    script.append("-- Juego de caracteres de origen: ").append(sourceCharacterSet).append('\n');
    script.append("-- Filas por tabla:\n");
    for (FrmTable table : FrmSnapshot.TABLES) {
      script
          .append("--   ")
          .append(table.tableName())
          .append(": ")
          .append(snapshot.rows().getOrDefault(table, List.of()).size())
          .append('\n');
    }
    script.append(
        """
        -- Como ejecutarlo: en una sesion nueva de SQL*Plus o SQLcl, sin trabajo pendiente,
        -- conectado al esquema duenio de las tablas FRM_*:
        --   sqlplus usuario@servicio @frm.sql
        -- El script es ASCII puro: funciona con cualquier NLS_LANG del cliente.
        -- Cada fila es un bloque PL/SQL terminado en "/". Es idempotente: una fila igual no se
        -- toca, una fila nueva se inserta y un id ocupado por otra fila aborta con ORA-20001; el
        -- WHENEVER SQLERROR deshace entonces todo lo hecho y nada queda escrito. Si hay texto
        -- no ASCII y la base de destino tiene otro juego de caracteres, aborta con ORA-20002
        -- antes de escribir nada.
        """);
  }

  private static void appendRow(
      StringBuilder script, FrmTable table, Map<String, Object> row, PackageKind kind) {
    String name = table.tableName();
    String idCondition = table.idColumn() + " = " + number(row.get(table.idColumn()));
    List<String> values = table.columns().subList(1, table.columns().size());
    List<String> correctable =
        kind == PackageKind.CORRECCION ? table.correctableColumns() : List.of();
    List<String> fixed = new ArrayList<>(values);
    fixed.removeAll(correctable);

    script.append("DECLARE\n");
    script.append("  ROWS_WITH_ID PLS_INTEGER;\n");
    script.append("  MATCHING_ROWS PLS_INTEGER;\n");
    for (String column : values) {
      script.append("  ").append(declaration(column, row.get(column))).append(";\n");
    }
    script.append("BEGIN\n");
    for (String column : values) {
      if (row.get(column) instanceof String text) {
        for (String part : textParts(text)) {
          script
              .append("  ")
              .append(variable(column))
              .append(" := ")
              .append(variable(column))
              .append(" || ")
              .append(part)
              .append(";\n");
        }
      }
    }
    script
        .append("  SELECT COUNT(*) INTO ROWS_WITH_ID FROM ")
        .append(name)
        .append(" WHERE ")
        .append(idCondition)
        .append(";\n");
    script.append("  IF ROWS_WITH_ID = 0 THEN\n");
    script
        .append("    INSERT INTO ")
        .append(name)
        .append(" (")
        .append(String.join(", ", table.columns()))
        .append(")\n");
    script.append("    VALUES (").append(number(row.get(table.idColumn())));
    for (String column : values) {
      script.append(", ").append(variable(column));
    }
    script.append(");\n");
    script.append("  ELSE\n");
    script
        .append("    SELECT COUNT(*) INTO MATCHING_ROWS FROM ")
        .append(name)
        .append(" WHERE ")
        .append(idCondition);
    for (String column : fixed) {
      script.append("\n      AND ").append(equality(column));
    }
    script.append(";\n");
    script.append("    IF MATCHING_ROWS <> ROWS_WITH_ID THEN\n");
    script
        .append("      RAISE_APPLICATION_ERROR(-20001, 'Id ocupado: ")
        .append(name)
        .append(' ')
        .append(idCondition.replace(" = ", "="))
        .append("');\n");
    script.append("    END IF;\n");
    if (!correctable.isEmpty()) {
      appendCorrection(script, name, idCondition, correctable);
    }
    script.append("  END IF;\n");
    script.append("END;\n");
    script.append("/\n");
  }

  /** Actualiza solo si algún texto difiere: una segunda ejecución no toca la fila. */
  private static void appendCorrection(
      StringBuilder script, String name, String idCondition, List<String> correctable) {
    script
        .append("    UPDATE ")
        .append(name)
        .append(" SET ")
        .append(
            correctable.stream()
                .map(column -> column + " = " + variable(column))
                .collect(Collectors.joining(", ")))
        .append('\n');
    script.append("     WHERE ").append(idCondition).append('\n');
    // CASE da un resultado de dos valores: con NOT y una columna nula la comparación quedaría
    // desconocida y la fila no se actualizaría.
    script.append("       AND CASE WHEN ");
    script.append(
        correctable.stream()
            .map(FrmScriptWriter::equality)
            .collect(Collectors.joining("\n                 AND ")));
    script.append("\n           THEN 0 ELSE 1 END = 1;\n");
  }

  /**
   * Un número va como NUMBER; un texto o un nulo, como VARCHAR2: un nulo comparado con una columna
   * NUMBER se convierte sin error, y un texto comparado con un NUMBER no.
   */
  private static String declaration(String column, Object value) {
    return switch (value) {
      case null -> variable(column) + " VARCHAR2(" + MAX_TEXT_BYTES + " BYTE)";
      case BigDecimal number -> variable(column) + " NUMBER := " + number(number);
      case String text -> variable(column) + " VARCHAR2(" + MAX_TEXT_BYTES + " BYTE)";
      default ->
          throw new IllegalStateException(
              "Tipo de valor no admitido en FRM_*: " + value.getClass().getSimpleName());
    };
  }

  private static String variable(String column) {
    return "V_" + column;
  }

  /** La igualdad nula-segura del brief, contra la variable del bloque. */
  private static String equality(String column) {
    String value = variable(column);
    return "(" + column + " = " + value + " OR (" + column + " IS NULL AND " + value + " IS NULL))";
  }

  private static String number(Object value) {
    if (!(value instanceof BigDecimal number)) {
      throw new IllegalStateException("Se esperaba un número en la fotografía: " + value);
    }
    return number.stripTrailingZeros().toPlainString();
  }

  /**
   * Las partes ASCII que concatenadas dan {@code text}. Cada una mide a lo sumo {@link
   * #MAX_PART_LENGTH} y ningún corte parte un punto de código. El {@code \} del texto va en UNISTR
   * como {@code \005C}: así no depende de {@code SET ESCAPE} ni del propio UNISTR.
   */
  static List<String> textParts(String text) {
    int bytes = text.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > MAX_TEXT_BYTES) {
      throw new IllegalStateException(
          "Un texto de FRM_* supera " + MAX_TEXT_BYTES + " bytes (" + bytes + ")");
    }
    List<String> parts = new ArrayList<>();
    StringBuilder ascii = new StringBuilder();
    StringBuilder unicode = new StringBuilder();
    int index = 0;
    while (index < text.length()) {
      int codePoint = text.codePointAt(index);
      index += Character.charCount(codePoint);
      if (isPlainAscii(codePoint)) {
        flushUnicode(parts, unicode);
        String quoted = codePoint == '\'' ? "''" : String.valueOf((char) codePoint);
        if (ascii.length() + quoted.length() + 2 > MAX_PART_LENGTH) {
          flushAscii(parts, ascii);
        }
        ascii.append(quoted);
      } else if (codePoint < 0x20 || codePoint == 0x7F) {
        flushAscii(parts, ascii);
        flushUnicode(parts, unicode);
        parts.add("CHR(" + codePoint + ")");
      } else {
        flushAscii(parts, ascii);
        StringBuilder units = new StringBuilder();
        for (char unit : Character.toChars(codePoint)) {
          units.append('\\').append(String.format("%04X", (int) unit));
        }
        int length = UNISTR_OPEN.length() + unicode.length() + units.length();
        if (length + UNISTR_CLOSE.length() > MAX_PART_LENGTH) {
          flushUnicode(parts, unicode);
        }
        unicode.append(units);
      }
    }
    flushAscii(parts, ascii);
    flushUnicode(parts, unicode);
    return parts;
  }

  /** Imprimible y sin significado para SQL*Plus ni UNISTR: todo menos controles y {@code \}. */
  private static boolean isPlainAscii(int codePoint) {
    return codePoint >= 0x20 && codePoint < 0x7F && codePoint != '\\';
  }

  private static void flushAscii(List<String> parts, StringBuilder ascii) {
    if (!ascii.isEmpty()) {
      parts.add("'" + ascii + "'");
      ascii.setLength(0);
    }
  }

  private static void flushUnicode(List<String> parts, StringBuilder unicode) {
    if (!unicode.isEmpty()) {
      parts.add(UNISTR_OPEN + unicode + UNISTR_CLOSE);
      unicode.setLength(0);
    }
  }
}
