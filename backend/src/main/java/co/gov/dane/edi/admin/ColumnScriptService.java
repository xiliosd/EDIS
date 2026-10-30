package co.gov.dane.edi.admin;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Genera como texto los scripts DDL que ejecuta la persona técnica (D3: la aplicación nunca crea
 * tablas ni columnas). Es puro: valida los identificadores con los mismos patrones del resto del
 * módulo y nunca toca la base.
 */
@Component
public class ColumnScriptService {

  public static final Pattern TABLE = Pattern.compile("[A-Z][A-Z0-9_]{1,29}");
  public static final Pattern COLUMN = Pattern.compile("[A-Z][0-9A-Z_]{1,29}");
  public static final String VARCHAR2 = "VARCHAR2";
  public static final String NUMBER = "NUMBER";
  public static final int MAX_VARCHAR2_LENGTH = 4000;
  public static final int MAX_NUMBER_PRECISION = 38;

  /** {@code ALTER TABLE T ADD (C VARCHAR2(n));}; para NUMBER, {@code length} es la precisión. */
  public String alterTable(String table, String column, String type, Integer length) {
    requireTable(table);
    if (column == null || !COLUMN.matcher(column).matches()) {
      throw new IllegalArgumentException("Nombre de columna inválido");
    }
    return "ALTER TABLE " + table + " ADD (" + column + " " + definitionOf(type, length) + ");";
  }

  /** Copia vacía de la estructura de una tabla de respuestas para un año nuevo. */
  public String createTableLike(String newTable, String sourceTable) {
    requireTable(newTable);
    requireTable(sourceTable);
    return "CREATE TABLE " + newTable + " AS SELECT * FROM " + sourceTable + " WHERE 1 = 0;";
  }

  /** Mensaje en español si el tipo y la longitud no son coherentes; nulo si lo son. */
  public static String describeProblem(String type, Integer length) {
    String normalized = type == null ? "" : type.toUpperCase(Locale.ROOT);
    if (length == null) {
      return "Indique la longitud de la columna.";
    }
    if (VARCHAR2.equals(normalized)) {
      return length >= 1 && length <= MAX_VARCHAR2_LENGTH
          ? null
          : "La longitud de VARCHAR2 debe estar entre 1 y 4000.";
    }
    if (NUMBER.equals(normalized)) {
      return length >= 1 && length <= MAX_NUMBER_PRECISION
          ? null
          : "La precisión de NUMBER debe estar entre 1 y 38.";
    }
    return "El tipo de columna debe ser VARCHAR2 o NUMBER.";
  }

  private static String definitionOf(String type, Integer length) {
    String problem = describeProblem(type, length);
    if (problem != null) {
      throw new IllegalArgumentException(problem);
    }
    return type.toUpperCase(Locale.ROOT) + "(" + length + ")";
  }

  private static void requireTable(String table) {
    if (table == null || !TABLE.matcher(table).matches()) {
      throw new IllegalArgumentException("Nombre de tabla inválido");
    }
  }
}
