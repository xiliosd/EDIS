import { QueryCache, QueryClient } from '@tanstack/react-query';
import { ApiError } from './api-error';

export const SESSION_QUERY_KEY = ['session'] as const;

function isClientError(error: unknown): boolean {
  return error instanceof ApiError && error.status >= 400 && error.status < 500;
}

export function createAppQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({
      onError: (error) => {
        // Sesión vencida en cualquier consulta: RequireRole lleva al usuario a ingresar de nuevo.
        if (error instanceof ApiError && error.status === 401) {
          client.setQueryData(SESSION_QUERY_KEY, null);
        }
      },
    }),
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => !isClientError(error) && failureCount < 2,
        refetchOnWindowFocus: false,
      },
    },
  });
  return client;
}
