import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { logout } from '../features/auth/auth-api';
import { useSession } from '../features/auth/use-session';
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
    let serverLogoutFailed = false;
    try {
      await logout();
    } catch {
      // La cookie podría seguir vigente en el servidor: se avisa al usuario en el ingreso.
      serverLogoutFailed = true;
    }
    clearAfterNavigation.current = true;
    navigate('/ingresar', {
      replace: true,
      state: serverLogoutFailed ? { logoutFailed: true } : null,
    });
  }

  return (
    <Button variant="secondary" onClick={() => void handleLogout()}>
      Cerrar sesión
    </Button>
  );
}
