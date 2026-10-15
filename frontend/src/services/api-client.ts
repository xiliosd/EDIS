import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './api-schema';

const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export function readCookie(
  name: string,
  cookieSource: string = document.cookie,
): string | undefined {
  for (const pair of cookieSource.split(';')) {
    const [rawName, ...rawValue] = pair.trim().split('=');
    if (rawName === name) {
      try {
        return decodeURIComponent(rawValue.join('='));
      } catch {
        // Valor con porcentaje mal formado: se trata como ausente en vez de romper la petición.
        return undefined;
      }
    }
  }
  return undefined;
}

// Spring Security valida el token de la cookie contra esta cabecera (double-submit cookie).
const csrfMiddleware: Middleware = {
  onRequest({ request }) {
    if (!SAFE_METHODS.has(request.method)) {
      const token = readCookie(CSRF_COOKIE_NAME);
      if (token) {
        request.headers.set(CSRF_HEADER_NAME, token);
      }
    }
    return request;
  },
};

// Las rutas del contrato ya incluyen /api/v1; el origen es el mismo del front (proxy).
export const apiClient = createClient<paths>({
  baseUrl: window.location.origin,
  credentials: 'include',
  // Resuelve fetch en cada llamada (no al importar) para que MSW pueda interceptarlo en pruebas.
  fetch: (request) => globalThis.fetch(request),
});
apiClient.use(csrfMiddleware);
