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

/** Producción: el cuestionario es de solo lectura. */
export function readOnlyEnvironment() {
  server.use(
    http.get('*/api/v1/admin/environment', () =>
      HttpResponse.json({ questionnaireWritesEnabled: false }),
    ),
  );
}
