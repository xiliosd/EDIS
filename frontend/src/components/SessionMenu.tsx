import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { logout } from '../features/auth/auth-api';
import { useSession } from '../features/auth/use-session';
import { SESSION_QUERY_KEY } from '../services/query-client';
import { Button } from './Button';

export function SessionMenu() {
  const { data: session } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

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
    // Se navega antes de vaciar la sesión: así RequireRole no redirige primero con su propio estado.
    navigate('/ingresar', {
      replace: true,
      state: serverLogoutFailed ? { logoutFailed: true } : null,
    });
    // Aunque el servidor no responda, la sesión local se cierra para no dejar datos visibles.
    queryClient.setQueryData(SESSION_QUERY_KEY, null);
    queryClient.removeQueries({
      predicate: (query) => query.queryKey[0] !== SESSION_QUERY_KEY[0],
    });
  }

  return (
    <Button variant="secondary" onClick={() => void handleLogout()}>
      Cerrar sesión
    </Button>
  );
}
