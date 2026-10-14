import { apiClient } from '../../services/api-client';
import { ApiError, unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';

export type Session = components['schemas']['SessionResponse'];
export type LoginRequest = components['schemas']['LoginRequest'];

/** null significa "sin sesión": no es un error para la interfaz. */
export async function fetchSession(): Promise<Session | null> {
  try {
    return await unwrap(apiClient.GET('/api/v1/auth/me'));
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export async function login(request: LoginRequest): Promise<Session> {
  // Obtiene la cookie XSRF-TOKEN antes del primer POST.
  await unwrap(apiClient.GET('/api/v1/auth/csrf'));
  return unwrap(apiClient.POST('/api/v1/auth/login', { body: request }));
}

export async function logout(): Promise<void> {
  await unwrap(apiClient.POST('/api/v1/auth/logout'));
}
