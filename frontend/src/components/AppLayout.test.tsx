import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AppLayout } from './AppLayout';
import { NotFoundPage } from '../pages/NotFoundPage';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('AppLayout', () => {
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
});
