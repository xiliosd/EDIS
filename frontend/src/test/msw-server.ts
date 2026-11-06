import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

/**
 * Cada prueba registra sus propios handlers con server.use(...). El único handler base es el modo
 * del ambiente: por defecto se puede escribir (desarrollo y QA); las pruebas de solo lectura lo
 * reemplazan con readOnlyEnvironment().
 */
export const server = setupServer(
  http.get('*/api/v1/admin/environment', () =>
    HttpResponse.json({ questionnaireWritesEnabled: true }),
  ),
);

/** No se pudo leer el modo del ambiente: el front lo trata como solo lectura. */
export function failingEnvironment() {
  server.use(
    http.get('*/api/v1/admin/environment', () =>
      HttpResponse.json(
        { title: 'Error interno', status: 500, detail: 'Ocurrió un error inesperado.' },
        { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    ),
  );
}

/** Producción: el cuestionario es de solo lectura. */
export function readOnlyEnvironment() {
  server.use(
    http.get('*/api/v1/admin/environment', () =>
      HttpResponse.json({ questionnaireWritesEnabled: false }),
    ),
  );
}
