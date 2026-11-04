package co.gov.dane.edi.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Ejecuta un {@code frm.sql} sin SQL*Plus: cada bloque PL/SQL termina en una línea con {@code /}.
 * Omite los {@code SET} y {@code WHENEVER} de la cabecera, los comentarios y el {@code COMMIT;}
 * final y maneja la transacción él mismo: ante el primer error hace rollback, igual que {@code
 * WHENEVER SQLERROR EXIT FAILURE ROLLBACK}, y relanza el error.
 *
 * <p>Es más rápido que {@link SqlPlusRunner} pero no reproduce el límite de línea ni la conversión
 * de caracteres del cliente: las pruebas de textos hostiles usan el SQL*Plus real.
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
        // Sin el escape JDBC un texto con {fn x} llega a Oracle igual que desde SQL*Plus.
        statement.setEscapeProcessing(false);
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
        || line.equals("SET AUTOCOMMIT OFF")
        || line.startsWith("WHENEVER SQLERROR ")
        || line.startsWith("WHENEVER OSERROR ")
        || line.equals("COMMIT;");
  }

  private static boolean isBlockStart(String line) {
    return line.equals("DECLARE") || line.equals("BEGIN");
  }
}
