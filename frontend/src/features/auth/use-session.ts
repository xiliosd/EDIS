import { useQuery } from '@tanstack/react-query';
import { SESSION_QUERY_KEY } from '../../services/query-client';
import { fetchSession, type Session } from './auth-api';

export const ADMIN_ROLES = [
  'ADMIN_NACIONAL',
  'ADMIN_TERRITORIAL',
  'ADMIN_OPERATIVO',
  'ADMIN_CUESTIONARIO',
] as const;

export function useSession() {
  return useQuery({ queryKey: SESSION_QUERY_KEY, queryFn: fetchSession, staleTime: 60_000 });
}

export function hasAnyRole(session: Session | null | undefined, roles: readonly string[]): boolean {
  return Boolean(session?.roles?.some((role) => roles.includes(role)));
}

export const LOGIN_PATH = '/ingresar';

export function homePathFor(session: Session): string {
  return hasAnyRole(session, ['ENCUESTADO']) ? '/encuesta' : '/admin';
}
