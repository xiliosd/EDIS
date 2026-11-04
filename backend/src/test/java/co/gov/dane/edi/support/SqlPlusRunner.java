package co.gov.dane.edi.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.oracle.OracleContainer;

/**
 * Ejecuta un {@code frm.sql} con el SQL*Plus real de la imagen de Oracle, igual que la persona
 * técnica en producción. El cliente va en WE8MSWIN1252 a propósito: el script es ASCII puro y no
 * debe depender del juego de caracteres del cliente.
 */
public final class SqlPlusRunner {

  /** Resultado de una ejecución: código de salida y lo que SQL*Plus escribió. */
  public record Run(int exitCode, String output) {}

  private static final String SCRIPT_PATH = "/tmp/frm.sql";
  private static final String CLIENT_NLS_LANG = "AMERICAN_AMERICA.WE8MSWIN1252";

  private final OracleContainer oracle;

  public SqlPlusRunner(OracleContainer oracle) {
    this.oracle = oracle;
  }

  public Run run(String script) throws IOException, InterruptedException {
    oracle.copyFileToContainer(
        Transferable.of(script.getBytes(StandardCharsets.US_ASCII)), SCRIPT_PATH);
    String connect =
        oracle.getUsername()
            + "/"
            + oracle.getPassword()
            + "@//localhost:1521/"
            + oracle.getDatabaseName();
    ExecResult result =
        oracle.execInContainer(
            "bash",
            "-c",
            "export NLS_LANG="
                + CLIENT_NLS_LANG
                + "; \"$ORACLE_HOME/bin/sqlplus\" -S -L \"$1\" @"
                + SCRIPT_PATH
                + " < /dev/null",
            "sqlplus-runner",
            connect);
    return new Run(result.getExitCode(), result.getStdout() + result.getStderr());
  }
}
