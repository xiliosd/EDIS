import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';

export type AdminEnvironment = components['schemas']['AdminEnvironment'];

export const ADMIN_ENVIRONMENT_QUERY_KEY = ['admin', 'environment'] as const;

export function fetchAdminEnvironment(): Promise<AdminEnvironment> {
  return unwrap(apiClient.GET('/api/v1/admin/environment'));
}

/**
 * true cuando el backend confirma que en este ambiente (producción) el cuestionario es de solo
 * lectura. Mientras carga o si la consulta falla se dejan los controles como siempre: el backend
 * igual rechaza la escritura con 403 y su mensaje se muestra donde se intentó.
 */
export function useQuestionnaireReadOnly(): boolean {
  const query = useQuery({
    queryKey: ADMIN_ENVIRONMENT_QUERY_KEY,
    queryFn: fetchAdminEnvironment,
    // El modo lo fija el despliegue: no cambia mientras la página está abierta.
    staleTime: Infinity,
  });
  return query.data?.questionnaireWritesEnabled === false;
}
