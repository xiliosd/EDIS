import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { QuestionnaireTabs } from './QuestionnaireTabs';

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <QuestionnaireTabs survey="EDIN" year={2027} />
    </MemoryRouter>,
  );
}

describe('QuestionnaireTabs', () => {
  it('ofrece las tres secciones en una navegación con nombre', () => {
    renderAt('/admin/cuestionario/EDIN/2027');

    expect(
      screen.getByRole('navigation', { name: 'Secciones del cuestionario' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Preguntas' })).toHaveAttribute(
      'href',
      '/admin/cuestionario/EDIN/2027',
    );
    expect(screen.getByRole('link', { name: 'Reglas' })).toHaveAttribute(
      'href',
      '/admin/cuestionario/EDIN/2027/reglas',
    );
    expect(screen.getByRole('link', { name: 'Simulador' })).toHaveAttribute(
      'href',
      '/admin/cuestionario/EDIN/2027/simulador',
    );
  });

  it.each([
    ['/admin/cuestionario/EDIN/2027', 'Preguntas'],
    ['/admin/cuestionario/EDIN/2027/reglas', 'Reglas'],
    ['/admin/cuestionario/EDIN/2027/simulador', 'Simulador'],
  ])('en %s marca %s como página actual', (path, current) => {
    renderAt(path);

    for (const name of ['Preguntas', 'Reglas', 'Simulador']) {
      const link = screen.getByRole('link', { name });
      if (name === current) {
        expect(link).toHaveAttribute('aria-current', 'page');
      } else {
        expect(link).not.toHaveAttribute('aria-current');
      }
    }
  });
});
