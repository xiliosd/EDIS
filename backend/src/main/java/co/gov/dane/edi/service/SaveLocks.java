package co.gov.dane.edi.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Serializa los guardados de un mismo encuestado (doble clic, dos pestañas) en la única instancia
 * del backend (D6). Candados por hash: memoria fija sin importar cuántos encuestados haya.
 *
 * <p>Las escrituras estructurales del administrador (FRM_* y archivos de reglas) usan un monitor
 * propio, {@link #withAdminLock}: una copia de año larga nunca bloquea el guardado de un
 * encuestado, como pasaría si compartiera franja con su clave.
 */
@Component
public class SaveLocks {

  private static final int STRIPES = 64;
  private final Object[] locks = new Object[STRIPES];
  private final Object adminLock = new Object();

  public SaveLocks() {
    for (int i = 0; i < STRIPES; i++) {
      locks[i] = new Object();
    }
  }

  public <T> T withLock(String key, Supplier<T> action) {
    synchronized (locks[Math.floorMod(key.hashCode(), STRIPES)]) {
      return action.get();
    }
  }

  /** Candado global de la administración del cuestionario, separado de los de encuestados. */
  public <T> T withAdminLock(Supplier<T> action) {
    synchronized (adminLock) {
      return action.get();
    }
  }
}
