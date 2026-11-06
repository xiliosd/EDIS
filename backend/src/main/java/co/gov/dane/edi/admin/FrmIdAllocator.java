package co.gov.dane.edi.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Asigna ids de las tablas FRM_* como MAX(id)+1 (el DDL real no define secuencias: decisión al
 * inicio de db/dev/schema.sql). El bloqueo exclusivo de la tabla dura hasta el fin de la
 * transacción del llamador, que debe insertar la fila antes de confirmar; así dos escrituras
 * simultáneas nunca obtienen el mismo id.
 */
@Component
public class FrmIdAllocator {

  private final JdbcTemplate jdbc;

  public FrmIdAllocator(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long next(FrmTable table) {
    if (table == FrmTable.RTA_GRUPO) {
      // Clave compuesta (COD_GRUPO, GRUPO): MAX(COD_GRUPO) + 1 no identifica una fila.
      throw new IllegalArgumentException("FRM_RTA_GRUPO no tiene id propio para MAX+1");
    }
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("La asignación de ids requiere una transacción activa");
    }
    jdbc.execute("LOCK TABLE " + table.tableName() + " IN EXCLUSIVE MODE");
    Long maximum =
        jdbc.queryForObject(
            "SELECT NVL(MAX(" + table.idColumn() + "), 0) FROM " + table.tableName(), Long.class);
    return (maximum == null ? 0 : maximum) + 1;
  }
}
