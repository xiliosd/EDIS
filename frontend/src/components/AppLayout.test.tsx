import type { ReactNode } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AppLayout } from './AppLayout';
import { NotFoundPage } from '../pages/NotFoundPage';

function renderAt(path: string, headerActions?: ReactNode) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<AppLayout headerActions={headerActions} />}>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('AppLayout', () => {
  it('renderiza las acciones del encabezado dentro del banner', () => {
    renderAt('/cualquiera', <button type="button">Cerrar sesión</button>);
    expect(
      within(screen.getByRole('banner')).getByRole('button', { name: 'Cerrar sesión' }),
    ).toBeInTheDocument();
  });

  it('muestra el encabezado institucional con texto alternativo', () => {
    renderAt('/cualquiera');
    expect(screen.getByRole('img', { name: /EDI-EDID/ })).toBeInTheDocument();
  });

  it('ofrece un enlace para saltar al contenido principal', () => {
    renderAt('/cualquiera');
    expect(screen.getByRole('link', { name: 'Saltar al contenido' })).toHaveAttribute(
      'href',
      '#contenido',
    );
    expect(screen.getByRole('main')).toHaveAttribute('id', 'contenido');
  });

  it('muestra la página no encontrada con un enlace de regreso', () => {
    renderAt('/no-existe');
    expect(screen.getByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir al inicio' })).toHaveAttribute('href', '/');
  });

  it('lleva el foco al contenido principal al cambiar de ruta, no en la primera carga', async () => {
    render(
      <MemoryRouter initialEntries={['/uno']}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="uno" element={<Link to="/dos">Ir a dos</Link>} />
            <Route path="dos" element={<h1>Página dos</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByRole('main')).not.toHaveFocus();

    await userEvent.click(screen.getByRole('link', { name: 'Ir a dos' }));

    expect(screen.getByRole('heading', { name: 'Página dos' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveFocus();
  });
});
