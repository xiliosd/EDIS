import { useEffect } from 'react';
import { fetchSession } from '../auth/auth-api';

// La sesión dura 30 minutos (spec §7.2); renovarla cada 10 da margen aunque falle una vez.
export const KEEP_ALIVE_INTERVAL_MS = 600_000;

export const UNSAVED_CHANGES_MESSAGE = 'Tiene respuestas sin guardar. ¿Desea salir?';

// Formularios montados con cambios sin guardar. Vive fuera de React para que el menú de sesión,
// que está en el encabezado y no en la página, pueda preguntar antes de cerrar la sesión.
let dirtyFormCount = 0;

export function hasUnsavedChanges(): boolean {
  return dirtyFormCount > 0;
}

/** Evita perder respuestas sin guardar: aviso al cerrar la pestaña y sesión viva. */
export function useUnsavedChanges(dirty: boolean): void {
  useEffect(() => {
    if (!dirty) {
      return undefined;
    }
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    dirtyFormCount += 1;
    window.addEventListener('beforeunload', warn);
    const timer = window.setInterval(() => {
      void fetchSession().catch(() => undefined);
    }, KEEP_ALIVE_INTERVAL_MS);
    return () => {
      dirtyFormCount -= 1;
      window.removeEventListener('beforeunload', warn);
      window.clearInterval(timer);
    };
  }, [dirty]);
}
