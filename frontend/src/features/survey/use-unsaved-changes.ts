import { useEffect } from 'react';
import { fetchSession } from '../auth/auth-api';

// La sesión dura 30 minutos (spec §7.2); renovarla cada 10 da margen aunque falle una vez.
export const KEEP_ALIVE_INTERVAL_MS = 600_000;

/** Evita perder respuestas sin guardar: aviso al cerrar la pestaña y sesión viva. */
export function useUnsavedChanges(dirty: boolean): void {
  useEffect(() => {
    if (!dirty) {
      return undefined;
    }
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    const timer = window.setInterval(() => {
      void fetchSession().catch(() => undefined);
    }, KEEP_ALIVE_INTERVAL_MS);
    return () => {
      window.removeEventListener('beforeunload', warn);
      window.clearInterval(timer);
    };
  }, [dirty]);
}
