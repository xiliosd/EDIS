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
    try {
      await logout();
    } finally {
      // Aunque el servidor no responda, la sesión local se cierra para no dejar datos visibles.
      queryClient.setQueryData(SESSION_QUERY_KEY, null);
      queryClient.removeQueries({
        predicate: (query) => query.queryKey[0] !== SESSION_QUERY_KEY[0],
      });
      navigate('/ingresar', { replace: true });
    }
  }

  return (
    <Button variant="secondary" onClick={() => void handleLogout()}>
      Cerrar sesión
    </Button>
  );
}
