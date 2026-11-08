import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';
import { hasAnyRole, useSession } from '../auth/use-session';

export type OperationScope = components['schemas']['OperationalScope'];
export type ScopeDepartment = components['schemas']['Department'];

/** Roles que operan reportes y gestión del operativo; ADMIN_CUESTIONARIO no tiene alcance. */
export const OPERATION_ROLES = ['ADMIN_NACIONAL', 'ADMIN_TERRITORIAL', 'ADMIN_OPERATIVO'] as const;

export const OPERATION_SCOPE_QUERY_KEY = ['admin', 'operation', 'scope'] as const;

export function fetchOperationScope(): Promise<OperationScope> {
  return unwrap(apiClient.GET('/api/v1/admin/operation/scope'));
}

/**
 * Departamentos y permisos del usuario en el operativo. Solo se consulta con un rol operativo:
 * con otro el servidor responde 403.
 */
export function useOperationScope() {
  const { data: session } = useSession();
  return useQuery({
    queryKey: OPERATION_SCOPE_QUERY_KEY,
    queryFn: fetchOperationScope,
    staleTime: 60_000,
    enabled: hasAnyRole(session, OPERATION_ROLES),
  });
}
