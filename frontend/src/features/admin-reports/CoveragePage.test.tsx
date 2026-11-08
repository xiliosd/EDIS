import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as downloadFile from '../../services/download-file';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';
import type { CoverageReport } from './reports-api';

const COVERAGE_URL = '/admin/reportes/cobertura';
const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const PROBLEM = { 'Content-Type': 'application/problem+json' };

const SCOPE_ALL = {
  allDepartments: true,
  canManageKeys: false,
  canManageNovelties: false,
  defaultYear: 2026,
  departments: [
    { code: 5, name: 'Antioquia' },
    { code: 8, name: 'Atlántico' },
    { code: 76, name: 'Valle' },
  ],
};

const SCOPE_TERRITORIAL = {
  allDepartments: false,
  canManageKeys: false,
  canManageNovelties: true,
  defaultYear: 2026,
  departments: [
    { code: 5, name: 'Antioquia' },
    { code: 8, name: 'Atlántico' },
  ],
};

function totals(sample: number, completePct: number) {
  return {
    sample,
    effectiveSample: sample - 1,
    novelties: 1,
    complete: 1234,
    completePct,
    incomplete: 20,
    incompletePct: 10,
    notStarted: 5,
    notStartedPct: 27.5,
  };
}

function report(coversAllDepartments: boolean): CoverageReport {
  return {
    survey: 'GENERAL',
    year: 2026,
    generatedAt: '2026-11-07T15:00:00Z',
    coversAllDepartments,
    departmentCount: 1,
    entityCount: 1,
    departments: [
      {
        code: 5,
        name: 'Antioquia',
        totals: totals(90, 50),
        entities: [
          {
            code: 1001,
            name: 'Alcaldía Ficticia de Prueba',
            totals: totals(90, 62.5),
            webLevels: [
              { level: 1, totals: totals(4, 25) },
              { level: 2, totals: totals(3, 30) },
            ],
          },
        ],
      },
    ],
    national: totals(90, 50),
    nationalWebLevels: [{ level: 1, totals: totals(4, 25) }],
  };
}

function serve(roles: string[], scope: typeof SCOPE_ALL, coversAll = true) {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({ accountType: 'ADMINISTRATOR', roles, departmentCode: '5' }),
    ),
    http.get('*/api/v1/admin/operation/scope', () => HttpResponse.json(scope)),
    http.get('*/api/v1/admin/reports/years', () => HttpResponse.json([2026, 2025])),
    http.get('*/api/v1/admin/reports/coverage', () => HttpResponse.json(report(coversAll))),
  );
}

async function openPage() {
  const app = renderApp(COVERAGE_URL);
  await screen.findByRole('heading', { name: 'Cobertura' });
  await screen.findByRole('table', { name: /Cobertura General 2026/ });
  return app;
}

afterEach(() => vi.restoreAllMocks());

describe('CoveragePage', () => {
  it('showsTheEntityOnceWithFormattedFigures', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    await openPage();

    expect(screen.getAllByText('Alcaldía Ficticia de Prueba')).toHaveLength(1);
    expect(screen.getByText('62,50 %')).toBeInTheDocument();
    expect(screen.getAllByText('1.234').length).toBeGreaterThan(0);
    expect(screen.getByRole('rowheader', { name: 'Total Antioquia' })).toBeInTheDocument();
  });

  it('expandsTheWebLevelsOfAnEntity', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    await openPage();
    const user = userEvent.setup();
    const toggle = screen.getByRole('button', { name: /Alcaldía Ficticia de Prueba/ });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('Nivel 2 (carga web)')).not.toBeInTheDocument();

    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('Nivel 2 (carga web)')).toBeInTheDocument();
  });

  it('labelsTheConsolidatedAsNationalOnlyWhenItCoversEveryDepartment', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL, true);
    const first = await openPage();
    expect(screen.getByRole('heading', { name: 'Consolidado nacional' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Total nacional' })).toBeInTheDocument();
    first.unmount();

    serve(['ADMIN_TERRITORIAL'], SCOPE_TERRITORIAL, false);
    await openPage();
    expect(screen.getByRole('heading', { name: 'Consolidado del alcance' })).toBeInTheDocument();
    expect(
      screen.getByRole('rowheader', { name: 'Total del alcance consultado' }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/nacional/i)).not.toBeInTheDocument();
  });

  it('limitsTheDepartmentSelectorToTheScopeOfTheUser', async () => {
    serve(['ADMIN_TERRITORIAL'], SCOPE_TERRITORIAL, false);
    await openPage();

    const select = screen.getByLabelText('Departamento');
    const options = within(select)
      .getAllByRole('option')
      .map((option) => option.textContent);
    expect(options).toEqual(['Todos mis departamentos', 'Antioquia', 'Atlántico']);
    expect(screen.queryByRole('option', { name: 'Valle' })).not.toBeInTheDocument();
  });

  it('asksForTheSelectedSurveyAndDepartment', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    const queries: URLSearchParams[] = [];
    server.use(
      http.get('*/api/v1/admin/reports/coverage', ({ request }) => {
        queries.push(new URL(request.url).searchParams);
        return HttpResponse.json(report(true));
      }),
    );
    await openPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('tab', { name: 'EDI' }));
    await user.selectOptions(screen.getByLabelText('Departamento'), '8');

    await waitFor(() => {
      const last = queries.at(-1);
      expect(last?.get('encuesta')).toBe('EDI');
      expect(last?.get('departamento')).toBe('8');
    });
    expect(screen.getByRole('tab', { name: 'EDI' })).toHaveAttribute('aria-selected', 'true');
  });

  it('downloadsTheExcelWithTheNameOfTheServer', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    let query: URLSearchParams | undefined;
    server.use(
      http.get('*/api/v1/admin/reports/coverage/excel', ({ request }) => {
        query = new URL(request.url).searchParams;
        return new HttpResponse(new Uint8Array([80, 75]), {
          headers: {
            'Content-Type': XLSX,
            'Content-Disposition': 'attachment; filename="cobertura-GENERAL-2026-2026-11-07.xlsx"',
          },
        });
      }),
    );
    const download = vi.spyOn(downloadFile, 'downloadBlobFile').mockImplementation(() => {});
    await openPage();

    await userEvent.click(screen.getByRole('button', { name: 'Exportar a Excel' }));

    await waitFor(() => expect(download).toHaveBeenCalled());
    expect(download.mock.calls[0][0]).toBe('cobertura-GENERAL-2026-2026-11-07.xlsx');
    expect(query?.get('encuesta')).toBe('GENERAL');
  });

  it('showsAnErrorWhenTheExcelIsRejected', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    server.use(
      http.get('*/api/v1/admin/reports/coverage/excel', () =>
        HttpResponse.json(
          { title: 'Prohibido', status: 403, detail: 'El departamento no es de su alcance.' },
          { status: 403, headers: PROBLEM },
        ),
      ),
    );
    const download = vi.spyOn(downloadFile, 'downloadBlobFile').mockImplementation(() => {});
    await openPage();

    await userEvent.click(screen.getByRole('button', { name: 'Exportar a Excel' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El departamento no es de su alcance.',
    );
    expect(download).not.toHaveBeenCalled();
  });

  it('showsAnErrorWithRetryWhenTheReportFails', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    server.use(
      http.get('*/api/v1/admin/reports/coverage', () =>
        HttpResponse.json(
          { title: 'Sin tablas', status: 422, detail: 'Las tablas del año no tienen B07 o H06.' },
          { status: 422, headers: PROBLEM },
        ),
      ),
    );
    renderApp(COVERAGE_URL);

    expect(await screen.findByRole('alert')).toHaveTextContent('Las tablas del año no tienen');
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('movesBetweenSurveyTabsWithTheArrowKeys', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    await openPage();
    const user = userEvent.setup();
    const general = screen.getByRole('tab', { name: 'General' });

    general.focus();
    await user.keyboard('{ArrowLeft}');
    // Desde la primera pestaña, la flecha izquierda vuelve a la última.
    expect(screen.getByRole('tab', { name: 'EDID' })).toHaveFocus();
    expect(screen.getByRole('tab', { name: 'EDID' })).toHaveAttribute('aria-selected', 'true');

    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'General' })).toHaveFocus();
    expect(screen.getByRole('tab', { name: 'General' })).toHaveAttribute('aria-selected', 'true');

    await user.keyboard('{Home}');
    expect(screen.getByRole('tab', { name: 'General' })).toHaveAttribute('aria-selected', 'true');
  });

  it('retriesTheReportAfterAnError', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    let calls = 0;
    server.use(
      http.get('*/api/v1/admin/reports/coverage', () => {
        calls += 1;
        return calls === 1
          ? HttpResponse.json(
              { title: 'Error', status: 503, detail: 'Base no disponible.' },
              { status: 503, headers: PROBLEM },
            )
          : HttpResponse.json(report(true));
      }),
    );
    renderApp(COVERAGE_URL);

    await userEvent.click(await screen.findByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('table', { name: /Cobertura General 2026/ })).toBeVisible();
  });

  it('saysWhenThereIsNoSampleForTheFilters', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    server.use(
      http.get('*/api/v1/admin/reports/coverage', () =>
        HttpResponse.json({ ...report(true), departments: undefined }),
      ),
    );
    renderApp(COVERAGE_URL);

    expect(await screen.findByText('No hay muestra para los filtros elegidos.')).toBeVisible();
    expect(screen.queryByRole('table', { name: /Cobertura General/ })).not.toBeInTheDocument();
  });

  it('showsEntitiesWithoutWebLevelsAsPlainText', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    const base = report(true);
    const department = base.departments![0];
    server.use(
      http.get('*/api/v1/admin/reports/coverage', () =>
        HttpResponse.json({
          ...base,
          nationalWebLevels: undefined,
          departments: [
            {
              ...department,
              entities: [{ ...department.entities![0], webLevels: undefined }],
            },
            { ...department, code: 8, name: 'Atlántico', entities: undefined },
          ],
        }),
      ),
    );
    await openPage();

    expect(screen.getByText('Alcaldía Ficticia de Prueba')).toBeVisible();
    expect(
      screen.queryByRole('button', { name: /Alcaldía Ficticia de Prueba/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Total Atlántico' })).toBeVisible();
    expect(screen.queryByText(/Nivel 1 \(carga web\)/)).not.toBeInTheDocument();
  });

  it('hasNoAxeViolations', async () => {
    serve(['ADMIN_OPERATIVO'], SCOPE_ALL);
    await openPage();
    await userEvent.click(screen.getByRole('button', { name: /Alcaldía Ficticia de Prueba/ }));

    // jsdom no calcula estilos: el contraste se revisa en E2E y con contrast.test.ts.
    const options = { rules: { 'color-contrast': { enabled: false } } };
    expect((await axe.run(document.body, options)).violations).toEqual([]);
  });
});
