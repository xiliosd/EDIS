package co.gov.dane.edi.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Ejecuta un {@code frm.sql} sin SQL*Plus: cada bloque PL/SQL termina en una línea con {@code /}.
 * Omite {@code SET DEFINE OFF}, {@code WHENEVER SQLERROR ...}, los comentarios y el {@code COMMIT;}
 * final y maneja la transacción él mismo: ante el primer error hace rollback, igual que {@code
 * WHENEVER SQLERROR EXIT ROLLBACK}, y relanza el error.
 */
public final class FrmScriptRunner {

  private final DataSource dataSource;

  public FrmScriptRunner(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void run(String script) throws SQLException {
    List<String> blocks = blocks(script);
    try (Connection connection = dataSource.getConnection()) {
      boolean autoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        for (String block : blocks) {
          statement.execute(block);
        }
        connection.commit();
      } catch (SQLException exception) {
        connection.rollback();
        throw exception;
      } finally {
        connection.setAutoCommit(autoCommit);
      }
    }
  }

  static List<String> blocks(String script) {
    List<String> blocks = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String line : script.split("\n", -1)) {
      if (line.equals("/")) {
        blocks.add(current.toString());
        current.setLength(0);
      } else if (!current.isEmpty() || isBlockStart(line)) {
        current.append(line).append('\n');
      } else if (!isSkippedOutsideBlocks(line)) {
        throw new IllegalStateException("Línea fuera de un bloque: " + line);
      }
    }
    if (!current.toString().isBlank()) {
      throw new IllegalStateException("Bloque sin '/' de cierre: " + current);
    }
    return blocks;
  }

  private static boolean isSkippedOutsideBlocks(String line) {
    return line.isBlank()
        || line.startsWith("--")
        || line.equals("SET DEFINE OFF")
        || line.startsWith("WHENEVER SQLERROR ")
        || line.equals("COMMIT;");
  }

  private static boolean isBlockStart(String line) {
    return line.equals("DECLARE") || line.equals("BEGIN");
  }
}
