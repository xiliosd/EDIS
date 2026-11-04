package co.gov.dane.edi.admin;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
 */
@Component
public class FrmScriptWriter {

  /** Tope de VARCHAR2 en SQL con MAX_STRING_SIZE=STANDARD; el límite de 3a lo hace inalcanzable. */
  private static final int MAX_LITERAL_BYTES = 4000;

  /** Trozo de un literal largo: así ninguna línea del script se vuelve gigante. */
  private static final int LITERAL_CHUNK = 1000;

  private static final Pattern PROJECT_CODE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");

  public String write(FrmSnapshot snapshot, PackageKind kind) {
    if (!PROJECT_CODE.matcher(snapshot.project()).matches()) {
      throw new IllegalArgumentException("Código de encuesta no válido para el script");
    }
    StringBuilder script = new StringBuilder();
    script.append("SET DEFINE OFF\n");
    script.append("WHENEVER SQLERROR EXIT ROLLBACK\n");
    appendHeader(script, snapshot, kind);
    for (FrmTable table : FrmSnapshot.TABLES) {
      for (Map<String, Object> row : snapshot.rows().getOrDefault(table, List.of())) {
        appendRow(script, table, row, kind);
      }
    }
    script.append("COMMIT;\n");
    return script.toString();
  }

  private static void appendHeader(StringBuilder script, FrmSnapshot snapshot, PackageKind kind) {
    script.append("-- frm.sql generado por el administrador del cuestionario.\n");
    script.append("-- Encuesta: ").append(snapshot.project()).append('\n');
    script.append("-- Año: ").append(snapshot.year()).append('\n');
    script.append("-- Paquete: ").append(kind.jsonValue()).append('\n');
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
        -- Cómo ejecutarlo: con SQL*Plus o SQLcl, conectado al esquema dueño de las tablas FRM_* y
        -- con el cliente en UTF-8 (por ejemplo NLS_LANG=SPANISH_COLOMBIA.AL32UTF8):
        --   sqlplus usuario@servicio @frm.sql
        -- Cada fila es un bloque PL/SQL terminado en "/". Es idempotente: una fila igual no se
        -- toca, una fila nueva se inserta y un id ocupado por otra fila aborta con ORA-20001; el
        -- WHENEVER SQLERROR deshace entonces todo lo hecho y nada queda escrito.
        """);
  }

  private static void appendRow(
      StringBuilder script, FrmTable table, Map<String, Object> row, PackageKind kind) {
    String name = table.tableName();
    String idCondition = table.idColumn() + " = " + idLiteral(row.get(table.idColumn()));
    List<String> correctable =
        kind == PackageKind.CORRECCION ? table.correctableColumns() : List.of();
    List<String> fixed = new ArrayList<>(table.columns().subList(1, table.columns().size()));
    fixed.removeAll(correctable);

    script.append("DECLARE\n");
    script.append("  ROWS_WITH_ID PLS_INTEGER;\n");
    script.append("  MATCHING_ROWS PLS_INTEGER;\n");
    script.append("BEGIN\n");
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
    script
        .append("    VALUES (")
        .append(
            table.columns().stream()
                .map(column -> literal(row.get(column)))
                .collect(Collectors.joining(", ")))
        .append(");\n");
    script.append("  ELSE\n");
    script
        .append("    SELECT COUNT(*) INTO MATCHING_ROWS FROM ")
        .append(name)
        .append(" WHERE ")
        .append(idCondition);
    for (String column : fixed) {
      script.append("\n      AND ").append(equality(column, row.get(column)));
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
      appendCorrection(script, name, idCondition, correctable, row);
    }
    script.append("  END IF;\n");
    script.append("END;\n");
    script.append("/\n");
  }

  /** Actualiza solo si algún texto difiere: una segunda ejecución no toca la fila. */
  private static void appendCorrection(
      StringBuilder script,
      String name,
      String idCondition,
      List<String> correctable,
      Map<String, Object> row) {
    script
        .append("    UPDATE ")
        .append(name)
        .append(" SET ")
        .append(
            correctable.stream()
                .map(column -> column + " = " + literal(row.get(column)))
                .collect(Collectors.joining(", ")))
        .append('\n');
    // CASE da un resultado de dos valores: con NOT y una columna nula la comparación quedaría
    // desconocida y la fila no se actualizaría.
    script
        .append("     WHERE ")
        .append(idCondition)
        .append("\n       AND CASE WHEN ")
        .append(
            correctable.stream()
                .map(column -> equality(column, row.get(column)))
                .collect(Collectors.joining(" AND ")))
        .append(" THEN 0 ELSE 1 END = 1;\n");
  }

  /** Igualdad nula-segura contra un literal conocido: {@code COL IS NULL} o {@code COL = valor}. */
  private static String equality(String column, Object value) {
    return value == null ? column + " IS NULL" : "(" + column + " = " + literal(value) + ")";
  }

  private static String idLiteral(Object id) {
    if (!(id instanceof BigDecimal number)) {
      throw new IllegalStateException("Id no numérico en la fotografía: " + id);
    }
    return literal(number);
  }

  /** Literal SQL de un valor de la fotografía; las cadenas largas van en trozos concatenados. */
  static String literal(Object value) {
    return switch (value) {
      case null -> "NULL";
      case BigDecimal number -> number.stripTrailingZeros().toPlainString();
      case String text -> textLiteral(text);
      case LocalDateTime timestamp ->
          "TIMESTAMP '" + timestamp.format(FrmSnapshot.TIMESTAMP_FORMAT) + "'";
      default ->
          throw new IllegalStateException(
              "Tipo de valor no admitido en FRM_*: " + value.getClass().getSimpleName());
    };
  }

  /**
   * Comillas duplicadas y saltos de línea como {@code CHR(10)}/{@code CHR(13)}: un salto dentro del
   * literal permitiría una línea "/" o "." que SQL*Plus tomaría como fin del bloque.
   */
  private static String textLiteral(String text) {
    int bytes = text.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > MAX_LITERAL_BYTES) {
      throw new IllegalStateException(
          "Un texto de FRM_* supera " + MAX_LITERAL_BYTES + " bytes (" + bytes + ")");
    }
    List<String> parts = new ArrayList<>();
    StringBuilder chunk = new StringBuilder();
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == '\n' || character == '\r') {
        addChunk(parts, chunk);
        parts.add("CHR(" + (int) character + ")");
      } else {
        if (chunk.length() == LITERAL_CHUNK) {
          addChunk(parts, chunk);
        }
        chunk.append(character);
      }
    }
    addChunk(parts, chunk);
    if (parts.isEmpty()) {
      return "''";
    }
    StringBuilder literal = new StringBuilder(parts.get(0));
    for (int index = 1; index < parts.size(); index++) {
      // Dos trozos de texto seguidos solo salen de partir uno largo: van en otra línea.
      boolean longTextSplit =
          parts.get(index - 1).endsWith("'") && parts.get(index).startsWith("'");
      literal.append(longTextSplit ? "\n|| " : " || ").append(parts.get(index));
    }
    return literal.toString();
  }

  private static void addChunk(List<String> parts, StringBuilder chunk) {
    if (!chunk.isEmpty()) {
      parts.add("'" + chunk.toString().replace("'", "''") + "'");
      chunk.setLength(0);
    }
  }
}
