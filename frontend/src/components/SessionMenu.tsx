import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { logout } from '../features/auth/auth-api';
import { LOGIN_PATH, useSession } from '../features/auth/use-session';
import { clearAllDrafts, rotateSessionGeneration } from '../features/survey/page-drafts';
import {
  hasUnsavedChanges,
  UNSAVED_CHANGES_MESSAGE,
  withLeaveConfirmed,
} from '../features/survey/use-unsaved-changes';
import { SESSION_QUERY_KEY } from '../services/query-client';
import { Button } from './Button';

export function SessionMenu() {
  const { data: session } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const clearAfterNavigation = useRef(false);

  // react-router confirma la navegación en una transición: si la sesión se vaciara antes,
  // RequireRole (aún en la ruta anterior) redirigiría con su propio estado y se perdería el
  // aviso logoutFailed. Por eso se vacía cuando la nueva ubicación ya está confirmada.
  useEffect(() => {
    if (!clearAfterNavigation.current) {
      return;
    }
    clearAfterNavigation.current = false;
    // Aunque el servidor no responda, la sesión local se cierra para no dejar datos visibles.
    queryClient.setQueryData(SESSION_QUERY_KEY, null);
    queryClient.removeQueries({
      predicate: (query) => query.queryKey[0] !== SESSION_QUERY_KEY[0],
    });
  }, [location.key, queryClient]);

  if (!session) {
    return null;
  }

  async function handleLogout() {
    if (hasUnsavedChanges() && !window.confirm(UNSAVED_CHANGES_MESSAGE)) {
      return;
    }
    let serverLogoutFailed = false;
    try {
      await logout();
    } catch {
      // La cookie podría seguir vigente en el servidor: se avisa al usuario en el ingreso.
      serverLogoutFailed = true;
    }
    // Con o sin respuesta del servidor, ningún borrador queda para el siguiente usuario del equipo.
    // Las demás pestañas también descartan los suyos (cambio de generación).
    clearAllDrafts();
    rotateSessionGeneration();
    clearAfterNavigation.current = true;
    // La salida ya se confirmó arriba: la página con respuestas sin guardar no vuelve a preguntar.
    navigate(LOGIN_PATH, {
      replace: true,
      state: withLeaveConfirmed(serverLogoutFailed ? { logoutFailed: true } : {}),
    });
  }

  return (
    <Button variant="secondary" onClick={() => void handleLogout()}>
      Cerrar sesión
    </Button>
  );
}
