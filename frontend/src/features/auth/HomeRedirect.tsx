import { Navigate } from 'react-router';
import { ErrorState } from '../../components/ErrorState';
import { homePathFor, LOGIN_PATH, useSession } from './use-session';

export function HomeRedirect() {
  const { data: session, isPending, isError, refetch } = useSession();
  if (isPending) {
    return (
      <p role="status" aria-live="polite" className="text-ink-muted">
        Verificando su sesión…
      </p>
    );
  }
  if (isError) {
    return (
      <ErrorState message="No fue posible verificar su sesión." onRetry={() => void refetch()} />
    );
  }
  return <Navigate to={session ? homePathFor(session) : LOGIN_PATH} replace />;
}
