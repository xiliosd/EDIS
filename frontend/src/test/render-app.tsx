import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { App } from '../App';
import { createAppQueryClient } from '../services/query-client';

/**
 * Monta la aplicación completa en una ruta, con caché limpia y sin reintentos. Con varias rutas,
 * la última es la actual y las anteriores quedan en el historial (para probar el botón Atrás).
 * Usa un enrutador de datos, como main.tsx, para que `useBlocker` funcione.
 */
export function renderApp(route: string | string[]) {
  const queryClient = createAppQueryClient();
  queryClient.setDefaultOptions({ queries: { retry: false, refetchOnWindowFocus: false } });
  const router = createMemoryRouter([{ path: '*', element: <App /> }], {
    initialEntries: Array.isArray(route) ? route : [route],
  });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return { queryClient, router, ...view };
}
