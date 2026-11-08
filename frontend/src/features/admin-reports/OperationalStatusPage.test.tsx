import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as downloadFile from '../../services/download-file';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';
import type { PersonStatusRow } from './reports-api';

const STATUS_API = '*/api/v1/admin/reports/operational-status';
const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

const SCOPE = {
  allDepartments: false,
  canManageKeys: false,
  canManageNovelties: false,
  defaultYear: 2026,
  departments: [{ code: 5, name: 'Antioquia' }],
};

const COMPLETE_WITH_NOVELTY: PersonStatusRow = {
  entityCode: 900001,
  entityName: 'Entidad Ficticia Uno',
  level: 1,
  loadType: 'WEB',
  name: 'Ana Prueba',
  respondentCode: '1000001',
  status: 'COMPLETE',
  noveltyCode: 3,
  noveltyName: 'Retiro ficticio',
  missingChapters: [],
};
const NOVELTY: PersonStatusRow = {
  entityCode: 900001,
  entityName: 'Entidad Ficticia Uno',
  level: 2,
  loadType: 'WEB',
  name: 'Luis Prueba',
  respondentCode: '1000002',
  status: 'NOVELTY',
  noveltyCode: 8,
  noveltyName: 'Otra causa',
  otherCause: 'Licencia ficticia',
  missingChapters: ['C', 'D'],
};

function serve(total = 2, rows: PersonStatusRow[] = [COMPLETE_WITH_NOVELTY, NOVELTY]) {
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({
        accountType: 'ADMINISTRATOR',
        roles: ['ADMIN_TERRITORIAL'],
        departmentCode: '5',
      }),
    ),
    http.get('*/api/v1/auth/csrf', () => HttpResponse.json({ token: 'x' })),
    http.get('*/api/v1/admin/operation/scope', () => HttpResponse.json(SCOPE)),
    http.get('*/api/v1/admin/reports/years', () => HttpResponse.json([2026])),
    http.get(STATUS_API, ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json({ page: 0, size: 100, total, rows });
    }),
  );
  return queries;
}

async function openPage() {
  renderApp('/admin/reportes/estado');
  await screen.findByRole('table', { name: /Estado de los funcionarios/ });
}

afterEach(() => vi.restoreAllMocks());

describe('OperationalStatusPage', () => {
  it('showsTheStatusAsTextAndTheNoveltyOfACompleteOne', async () => {
    serve();
    await openPage();

    const complete = screen.getByRole('row', { name: /Ana Prueba/ });
    expect(within(complete).getByText('Completo')).toBeInTheDocument();
    expect(within(complete).getByText('Retiro ficticio')).toBeInTheDocument();
    const novelty = screen.getByRole('row', { name: /Luis Prueba/ });
    expect(within(novelty).getByText('Novedad (SD)')).toBeInTheDocument();
    expect(within(novelty).getByText('C, D')).toBeInTheDocument();
    expect(document.title).not.toMatch(/Prueba|1000001/);
  });

  it('queriesAgainWithTheChosenStatus', async () => {
    const queries = serve();
    await openPage();
    expect(queries.at(-1)?.get('estado')).toBe('ALL');
    expect(queries.at(-1)?.get('departamento')).toBe('5');

    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'NOVELTY');

    await waitFor(() => expect(queries.at(-1)?.get('estado')).toBe('NOVELTY'));
    expect(queries.at(-1)?.get('page')).toBe('0');
    expect(queries.at(-1)?.get('size')).toBe('100');
  });

  it('pagesOnTheServerWithAccessibleControls', async () => {
    const queries = serve(250);
    await openPage();
    const next = screen.getByRole('button', { name: 'Siguiente' });
    next.focus();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    expect(screen.getByText(/Página 1 de 3/)).toBeInTheDocument();

    await userEvent.click(next);

    await waitFor(() => expect(queries.at(-1)?.get('page')).toBe('1'));
    expect(await screen.findByText(/Página 2 de 3/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeEnabled();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Siguiente' }));
    expect(screen.getByRole('navigation', { name: 'Paginación' })).toContainElement(
      document.activeElement as HTMLElement,
    );
  });

  it('hidesTheTableWhenTheDepartmentIsCleared', async () => {
    serve();
    // Con un solo departamento el filtro lo vuelve a elegir solo: se necesitan dos.
    server.use(
      http.get('*/api/v1/admin/operation/scope', () =>
        HttpResponse.json({
          ...SCOPE,
          departments: [...SCOPE.departments, { code: 8, name: 'Atlántico' }],
        }),
      ),
    );
    renderApp('/admin/reportes/estado');
    await userEvent.selectOptions(await screen.findByLabelText('Departamento'), '5');
    await screen.findByRole('table', { name: /Estado de los funcionarios/ });

    await userEvent.selectOptions(screen.getByLabelText('Departamento'), '');

    await waitFor(() => expect(screen.queryByRole('table')).not.toBeInTheDocument());
    expect(screen.queryByRole('navigation', { name: 'Paginación' })).not.toBeInTheDocument();
    expect(screen.getByText(/Seleccione un departamento para ver/)).toBeInTheDocument();
  });

  it('downloadsTheExcelWithTheFilters', async () => {
    serve();
    let requested: URL | undefined;
    server.use(
      http.get(`${STATUS_API}/excel`, ({ request }) => {
        requested = new URL(request.url);
        return new HttpResponse('x', {
          headers: {
            'Content-Type': XLSX,
            'Content-Disposition': 'attachment; filename="estado-operativo-GENERAL.xlsx"',
          },
        });
      }),
    );
    const download = vi.spyOn(downloadFile, 'downloadBlobFile').mockImplementation(() => {});
    await openPage();

    await userEvent.click(screen.getByRole('button', { name: 'Exportar a Excel' }));

    await waitFor(() => expect(download).toHaveBeenCalled());
    expect(requested?.searchParams.get('departamento')).toBe('5');
    expect(requested?.searchParams.has('page')).toBe(false);
    expect(download.mock.calls[0][0]).toBe('estado-operativo-GENERAL.xlsx');
  });

  it('showsAnErrorStateWithRetry', async () => {
    serve();
    server.use(
      http.get(STATUS_API, () =>
        HttpResponse.json(
          { title: 'Error', detail: 'Falla ficticia', status: 500 },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderApp('/admin/reportes/estado');

    expect(await screen.findByRole('button', { name: /Reintentar/ })).toBeInTheDocument();
  });

  it('hasNoAxeViolations', async () => {
    serve();
    await openPage();

    const options = { rules: { 'color-contrast': { enabled: false } } };
    expect((await axe.run(document.body, options)).violations).toEqual([]);
  });
});
