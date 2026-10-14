import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { App } from '../App';
import { createAppQueryClient } from '../services/query-client';

/** Monta la aplicación completa en una ruta, con caché limpia y sin reintentos. */
export function renderApp(route: string) {
  const queryClient = createAppQueryClient();
  queryClient.setDefaultOptions({ queries: { retry: false, refetchOnWindowFocus: false } });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { queryClient, ...view };
}
