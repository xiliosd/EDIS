import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ErrorState } from '../../components/ErrorState';
import { AccessDeniedPage } from '../../pages/AccessDeniedPage';
import { hasAnyRole, useSession } from './use-session';

interface RequireRoleProps {
  roles: readonly string[];
  children: ReactNode;
}

export function RequireRole({ roles, children }: RequireRoleProps) {
  const { data: session, isPending, isError, refetch } = useSession();
  const location = useLocation();

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
  if (!session) {
    return <Navigate to="/ingresar" replace state={{ from: location.pathname }} />;
  }
  if (!hasAnyRole(session, roles)) {
    return <AccessDeniedPage />;
  }
  return children;
}
