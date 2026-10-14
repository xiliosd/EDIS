import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/msw-server';
import { apiClient, readCookie } from './api-client';
import { ApiError, unwrap } from './api-error';

describe('apiClient', () => {
  it('envía la cabecera CSRF en operaciones que modifican datos', async () => {
    document.cookie = 'XSRF-TOKEN=token-de-prueba; path=/';
    let receivedToken: string | null = null;
    server.use(
      http.post('*/api/v1/auth/logout', ({ request }) => {
        receivedToken = request.headers.get('X-XSRF-TOKEN');
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await unwrap(apiClient.POST('/api/v1/auth/logout'));

    expect(receivedToken).toBe('token-de-prueba');
  });

  it('no envía la cabecera CSRF en lecturas', async () => {
    document.cookie = 'XSRF-TOKEN=token-de-prueba; path=/';
    let receivedToken: string | null = 'sin-leer';
    server.use(
      http.get('*/api/v1/auth/csrf', ({ request }) => {
        receivedToken = request.headers.get('X-XSRF-TOKEN');
        return HttpResponse.json({ token: 'x' });
      }),
    );

    await unwrap(apiClient.GET('/api/v1/auth/csrf'));

    expect(receivedToken).toBeNull();
  });

  it('convierte un problem+json en ApiError con el detalle del servidor', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(
          { title: 'Credenciales inválidas', status: 401, detail: 'Usuario o clave incorrectos.' },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const error = await unwrap(
      apiClient.POST('/api/v1/auth/login', {
        body: { accountType: 'RESPONDENT', username: 'x', password: 'y' },
      }),
    ).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 401, detail: 'Usuario o clave incorrectos.' });
  });

  it('expone los errores por campo de un 400', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(
          { title: 'Datos inválidos', status: 400, errors: { username: 'Ingrese su usuario' } },
          { status: 400 },
        ),
      ),
    );

    const error = (await unwrap(
      apiClient.POST('/api/v1/auth/login', {
        body: { accountType: 'RESPONDENT', username: '', password: 'y' },
      }),
    ).catch((caught: unknown) => caught)) as ApiError;

    expect(error.fieldErrors).toEqual({ username: 'Ingrese su usuario' });
  });

  it('informa la falta de conexión con estado 0', async () => {
    server.use(http.get('*/api/v1/auth/me', () => HttpResponse.error()));

    const error = (await unwrap(apiClient.GET('/api/v1/auth/me')).catch(
      (caught: unknown) => caught,
    )) as ApiError;

    expect(error.status).toBe(0);
    expect(error.title).toBe('Sin conexión');
  });

  it('lee cookies por nombre exacto', () => {
    expect(readCookie('XSRF-TOKEN', 'OTRO=1; XSRF-TOKEN=abc%3D')).toBe('abc=');
    expect(readCookie('XSRF-TOKEN', 'X-XSRF-TOKEN=no')).toBeUndefined();
  });
});
