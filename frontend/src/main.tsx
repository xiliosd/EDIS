import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { App } from './App';
import { createAppQueryClient } from './services/query-client';
import './styles/tokens.css';

const queryClient = createAppQueryClient();
// Enrutador de datos (necesario para useBlocker); App conserva sus <Routes> bajo la ruta comodín.
const router = createBrowserRouter([{ path: '*', element: <App /> }]);
const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('No existe el elemento #root en index.html');
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
