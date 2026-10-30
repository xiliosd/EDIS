import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';

const YEARS = [
  { year: 2026, state: 'publicado', version: 1, active: true },
  { year: 2027, state: 'borrador', version: 3, active: false },
];

function loginAs(roles: string[]) {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({ accountType: 'ADMINISTRATOR', roles, departmentCode: '11' }),
    ),
  );
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token-de-prueba';
  loginAs(['ADMIN_CUESTIONARIO']);
});

describe('YearsPage', () => {
  it('listsYearsWithState', async () => {
    server.use(http.get('*/api/v1/admin/years/EDIN', () => HttpResponse.json(YEARS)));
    renderApp('/admin/cuestionario');

    expect(await screen.findByRole('heading', { name: 'Años del cuestionario' })).toBeVisible();
    const row2026 = (await screen.findByRole('link', { name: '2026' })).closest('tr')!;
    expect(within(row2026).getByText('Publicado')).toBeInTheDocument();
    expect(within(row2026).getByText('Sí')).toBeInTheDocument();
    const row2027 = screen.getByRole('link', { name: '2027' }).closest('tr')!;
    expect(within(row2027).getByText('Borrador')).toBeInTheDocument();
    expect(within(row2027).getByText('3')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '2027' })).toHaveAttribute(
      'href',
      '/admin/cuestionario/EDIN/2027',
    );
  });

  it('createsYearAndNavigatesToTree', async () => {
    let body: unknown;
    server.use(
      http.get('*/api/v1/admin/years/EDIN', () => HttpResponse.json([YEARS[0]])),
      http.post('*/api/v1/admin/years/EDIN', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          { year: 2027, state: 'borrador', version: 1, active: false },
          { status: 201 },
        );
      }),
    );
    renderApp('/admin/cuestionario');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Crear año' }));
    expect(screen.getByLabelText('Año nuevo')).toHaveValue('2027');
    expect(screen.getByLabelText('Año de origen')).toHaveValue('2026');
    await user.click(screen.getByRole('button', { name: 'Crear' }));

    expect(
      await screen.findByRole('heading', { name: 'Cuestionario EDIN 2027' }),
    ).toBeInTheDocument();
    expect(body).toEqual({ year: 2027, fromYear: 2026 });
  });

  it('showsConflictWhenDraftExists', async () => {
    server.use(
      http.get('*/api/v1/admin/years/EDIN', () => HttpResponse.json(YEARS)),
      http.post('*/api/v1/admin/years/EDIN', () =>
        HttpResponse.json(
          { title: 'Conflicto', detail: 'Ya existe un año en borrador para EDIN.' },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderApp('/admin/cuestionario');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Crear año' }));
    await user.click(screen.getByRole('button', { name: 'Crear' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ya existe un año en borrador para EDIN.',
    );
  });

  it('createWithInvalidInputsShowsErrors', async () => {
    let posted = false;
    server.use(
      http.get('*/api/v1/admin/years/EDIN', () => HttpResponse.json([])),
      http.post('*/api/v1/admin/years/EDIN', () => {
        posted = true;
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    renderApp('/admin/cuestionario');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Crear año' }));
    expect(screen.getByLabelText('Año nuevo')).toHaveValue('');
    await user.click(screen.getByRole('button', { name: 'Crear' }));

    expect(screen.getAllByText('Escriba un año de cuatro cifras.')).toHaveLength(2);
    expect(screen.getByLabelText('Año nuevo')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Año nuevo')).toHaveFocus();
    expect(posted).toBe(false);
  });

  it('reloadsYearsWhenSurveyChanges', async () => {
    server.use(
      http.get('*/api/v1/admin/years/EDIN', () => HttpResponse.json(YEARS)),
      http.get('*/api/v1/admin/years/EDID', () =>
        HttpResponse.json([{ year: 2025, state: 'publicado', version: 1, active: true }]),
      ),
    );
    renderApp('/admin/cuestionario');
    const user = userEvent.setup();

    await screen.findByRole('link', { name: '2026' });
    await user.selectOptions(screen.getByLabelText('Encuesta'), 'EDID');

    expect(await screen.findByRole('link', { name: '2025' })).toBeInTheDocument();
  });
});

describe('AdminLayout', () => {
  it('menuOnlyShowsQuestionnaireForRole', async () => {
    loginAs(['ADMIN_NACIONAL']);
    const first = renderApp('/admin');
    const nav = await screen.findByRole('navigation', { name: 'Administración' });
    expect(within(nav).getByRole('link', { name: 'Inicio' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Cuestionario' })).not.toBeInTheDocument();
    first.unmount();

    loginAs(['ADMIN_CUESTIONARIO']);
    renderApp('/admin');
    const navWithRole = await screen.findByRole('navigation', { name: 'Administración' });
    expect(await within(navWithRole).findByRole('link', { name: 'Cuestionario' })).toHaveAttribute(
      'href',
      '/admin/cuestionario',
    );
  });
});
