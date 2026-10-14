import { describe, expect, it } from 'vitest';
import { ApiError } from './api-error';
import { SESSION_QUERY_KEY, createAppQueryClient } from './query-client';

describe('createAppQueryClient', () => {
  it('cierra la sesión local cuando una consulta responde 401', async () => {
    const client = createAppQueryClient();
    client.setQueryData(SESSION_QUERY_KEY, { roles: ['ENCUESTADO'] });

    await client
      .fetchQuery({
        queryKey: ['capitulos'],
        queryFn: () => Promise.reject(new ApiError(401, 'Sesión requerida', 'Inicie sesión.')),
        retry: false,
      })
      .catch(() => undefined);

    expect(client.getQueryData(SESSION_QUERY_KEY)).toBeNull();
  });

  it('conserva la sesión ante otros errores', async () => {
    const client = createAppQueryClient();
    client.setQueryData(SESSION_QUERY_KEY, { roles: ['ENCUESTADO'] });

    await client
      .fetchQuery({
        queryKey: ['capitulos'],
        queryFn: () =>
          Promise.reject(new ApiError(503, 'Servicio no disponible', 'Intente luego.')),
        retry: false,
      })
      .catch(() => undefined);

    expect(client.getQueryData(SESSION_QUERY_KEY)).toEqual({ roles: ['ENCUESTADO'] });
  });
});
