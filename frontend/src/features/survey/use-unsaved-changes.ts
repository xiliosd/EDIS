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

// Estado de navegación que marca una salida ya confirmada (p. ej. al cerrar sesión), para que la
// página no vuelva a preguntar.
const LEAVE_CONFIRMED_STATE_KEY = 'salidaConfirmada';

export function withLeaveConfirmed(state: object): Record<string, unknown> {
  return { ...state, [LEAVE_CONFIRMED_STATE_KEY]: true };
}

export function isLeaveConfirmed(state: unknown): boolean {
  return (
    typeof state === 'object' &&
    state !== null &&
    (state as Record<string, unknown>)[LEAVE_CONFIRMED_STATE_KEY] === true
  );
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
