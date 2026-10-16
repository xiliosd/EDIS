package co.gov.dane.edi.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Serializa los guardados de un mismo encuestado (doble clic, dos pestañas) en la única instancia
 * del backend (D6). Candados por hash: memoria fija sin importar cuántos encuestados haya.
 */
@Component
public class SaveLocks {

  private static final int STRIPES = 64;
  private final Object[] locks = new Object[STRIPES];

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
}
