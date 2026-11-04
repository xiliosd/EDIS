package co.gov.dane.edi.admin;

import java.util.ArrayList;
import java.util.List;
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

  /** El código de encuesta entra en un comentario del script. */
  private static final Pattern PROJECT_CODE = Pattern.compile("[A-Za-z0-9_-]{1,40}");

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

  /** Tabla de respuestas que el año mapea y que se crea vacía a partir de la de otro año. */
  public record TableCopy(String table, String sourceTable) {}

  /** Columna nueva del año ({@code columnasNuevas}) en la tabla de su capítulo. */
  public record ColumnAddition(String table, String column, String type, Integer length) {}

  /**
   * {@code columnas.sql} del paquete de promoción: un bloque PL/SQL por tabla y por columna que
   * solo ejecuta el DDL si el objeto aún no existe en el esquema actual. Así se puede correr otra
   * vez sin error. Va primero cada tabla y luego las columnas, que pueden ser de esa tabla. Sin
   * nada que crear queda solo un comentario.
   */
  public String packageScript(
      String project, int year, List<TableCopy> tables, List<ColumnAddition> columns) {
    if (project == null || !PROJECT_CODE.matcher(project).matches()) {
      throw new IllegalArgumentException("Código de encuesta inválido");
    }
    List<String> blocks = new ArrayList<>();
    for (TableCopy copy : tables) {
      String ddl = withoutSemicolon(createTableLike(copy.table(), copy.sourceTable()));
      blocks.add(guardedBlock("ALL_TABLES", "TABLE_NAME = '" + copy.table() + "'", ddl));
    }
    for (ColumnAddition addition : columns) {
      String ddl =
          withoutSemicolon(
              alterTable(addition.table(), addition.column(), addition.type(), addition.length()));
      blocks.add(
          guardedBlock(
              "ALL_TAB_COLUMNS",
              "TABLE_NAME = '"
                  + addition.table()
                  + "' AND COLUMN_NAME = '"
                  + addition.column()
                  + "'",
              ddl));
    }
    StringBuilder script = new StringBuilder();
    if (blocks.isEmpty()) {
      script.append("-- columnas.sql: este paquete no crea tablas ni columnas de respuesta.\n");
      script.append("-- Encuesta: ").append(project).append('\n');
      script.append("-- Anio: ").append(year).append('\n');
      return script.toString();
    }
    script.append("SET DEFINE OFF\n");
    script.append("WHENEVER SQLERROR EXIT FAILURE ROLLBACK\n");
    script.append("WHENEVER OSERROR EXIT FAILURE ROLLBACK\n");
    script.append("-- columnas.sql generado por el administrador del cuestionario.\n");
    script.append("-- Encuesta: ").append(project).append('\n');
    script.append("-- Anio: ").append(year).append('\n');
    script.append(
        """
        -- Ejecutelo ANTES de frm.sql, en SQL*Plus o SQLcl, conectado al esquema duenio de las
        -- tablas de respuestas:
        --   sqlplus usuario@servicio @columnas.sql
        -- Cada tabla o columna solo se crea si aun no existe: se puede ejecutar otra vez.
        -- Atencion: el DDL de Oracle confirma solo. Lo que este script cree no se puede deshacer
        -- con ROLLBACK; si falla a mitad, lo ya creado queda y basta con volver a ejecutarlo.
        """);
    blocks.forEach(script::append);
    return script.toString();
  }

  private static String guardedBlock(String dictionaryView, String condition, String ddl) {
    return "DECLARE\n"
        + "  EXISTING PLS_INTEGER;\n"
        + "BEGIN\n"
        + "  SELECT COUNT(*) INTO EXISTING FROM "
        + dictionaryView
        + " WHERE OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND "
        + condition
        + ";\n"
        + "  IF EXISTING = 0 THEN\n"
        + "    EXECUTE IMMEDIATE '"
        + ddl
        + "';\n"
        + "  END IF;\n"
        + "END;\n"
        + "/\n";
  }

  private static String withoutSemicolon(String statement) {
    return statement.substring(0, statement.length() - 1);
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
