import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as downloadFile from '../../services/download-file';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';
import type { CutSummary } from './reports-api';

const CUTS_URL = '/admin/reportes/cortes';
const CUTS_API = '*/api/v1/admin/reports/cuts';
const PROBLEM = { 'Content-Type': 'application/problem+json' };
const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

const SCOPE = {
  allDepartments: false,
  canManageKeys: false,
  canManageNovelties: true,
  defaultYear: 2026,
  departments: [
    { code: 5, name: 'Antioquia' },
    { code: 8, name: 'Atlántico' },
  ],
};

const OLD_CUT: CutSummary = {
  id: '20261105-101500-a1b2c3',
  departmentCode: 5,
  departmentName: 'Antioquia',
  // 15:15 UTC son las 10:15 en Bogotá.
  generatedAt: '2026-11-05T15:15:00Z',
  generatedBy: 'ana.prueba',
  survey: 'GENERAL',
  year: 2026,
};

const NEW_CUT: CutSummary = {
  id: '20261107-190500-d4e5f6',
  departmentCode: 5,
  departmentName: 'Antioquia',
  // 00:05 UTC del día 8 son las 19:05 del 7 en Bogotá.
  generatedAt: '2026-11-08T00:05:00Z',
  generatedBy: 'luis.prueba',
  survey: 'GENERAL',
  year: 2026,
};

function serve(scope = SCOPE) {
  const stored: CutSummary[] = [OLD_CUT];
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({
        accountType: 'ADMINISTRATOR',
        roles: ['ADMIN_TERRITORIAL'],
        departmentCode: '5',
      }),
    ),
    http.get('*/api/v1/auth/csrf', () => HttpResponse.json({ token: 'x' })),
    http.get('*/api/v1/admin/operation/scope', () => HttpResponse.json(scope)),
    http.get('*/api/v1/admin/reports/years', () => HttpResponse.json([2026])),
    http.get(CUTS_API, () => HttpResponse.json([...stored])),
    http.post(CUTS_API, () => {
      stored.unshift(NEW_CUT);
      return HttpResponse.json(NEW_CUT);
    }),
  );
  return stored;
}

async function openPage() {
  const app = renderApp(CUTS_URL);
  await screen.findByRole('heading', { name: 'Cortes e histórico' });
  await screen.findByRole('table', { name: 'Cortes generados' });
  return app;
}

afterEach(() => vi.restoreAllMocks());

describe('CutsPage', () => {
  it('listsTheHistoryWithDateTimeAndAuthorInBogota', async () => {
    serve();
    await openPage();

    const row = screen.getByRole('row', { name: /ana\.prueba/ });
    expect(within(row).getByText('10:15:00')).toBeInTheDocument();
    expect(within(row).getByText('ana.prueba')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: /Descargar corte de Antioquia/ })).toBeVisible();
  });

  it('showsTheGeneratedCutAsANewRowAndSendsTheFilters', async () => {
    const stored = serve();
    let body: unknown;
    server.use(
      http.post(CUTS_API, async ({ request }) => {
        body = await request.json();
        stored.unshift(NEW_CUT);
        return HttpResponse.json(NEW_CUT);
      }),
    );
    await openPage();
    const user = userEvent.setup();

    await user.selectOptions(screen.getByLabelText('Departamento'), '5');
    expect(screen.getAllByRole('row')).toHaveLength(2);
    await user.click(screen.getByRole('button', { name: 'Generar corte' }));

    expect(await screen.findByText('luis.prueba')).toBeInTheDocument();
    // La fecha y la hora se muestran en Bogotá (19:05 del día 7), no en UTC.
    expect(screen.getByText('19:05:00')).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(3);
    expect(body).toEqual({ encuesta: 'GENERAL', departamento: 5 });
  });

  it('requiresADepartmentBeforeGenerating', async () => {
    serve();
    let posted = false;
    server.use(
      http.post(CUTS_API, () => {
        posted = true;
        return HttpResponse.json(NEW_CUT);
      }),
    );
    await openPage();

    await userEvent.click(screen.getByRole('button', { name: 'Generar corte' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Seleccione un departamento');
    expect(posted).toBe(false);
  });

  it('preselectsTheOnlyDepartmentOfTheUser', async () => {
    serve({ ...SCOPE, departments: [{ code: 8, name: 'Atlántico' }] });
    await openPage();

    await waitFor(() => expect(screen.getByLabelText('Departamento')).toHaveValue('8'));
  });

  it('showsAnErrorStateWhenTheServerRefusesTheCut', async () => {
    serve();
    server.use(
      http.post(CUTS_API, () =>
        HttpResponse.json(
          { title: 'Prohibido', status: 403, detail: 'El departamento no es de su alcance.' },
          { status: 403, headers: PROBLEM },
        ),
      ),
    );
    await openPage();
    const user = userEvent.setup();

    await user.selectOptions(screen.getByLabelText('Departamento'), '8');
    await user.click(screen.getByRole('button', { name: 'Generar corte' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El departamento no es de su alcance.',
    );
  });

  it('showsAnErrorStateWhenTheHistoryIsForbidden', async () => {
    serve();
    server.use(
      http.get(CUTS_API, () =>
        HttpResponse.json(
          { title: 'Prohibido', status: 403, detail: 'No tiene permiso para ver los cortes.' },
          { status: 403, headers: PROBLEM },
        ),
      ),
    );
    renderApp(CUTS_URL);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No tiene permiso para ver los cortes.',
    );
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('downloadsTheExcelOfACut', async () => {
    serve();
    let requestedPath = '';
    server.use(
      http.get(`${CUTS_API}/:id/excel`, ({ request }) => {
        requestedPath = new URL(request.url).pathname;
        return new HttpResponse(new Uint8Array([80, 75]), { headers: { 'Content-Type': XLSX } });
      }),
    );
    const download = vi.spyOn(downloadFile, 'downloadBlobFile').mockImplementation(() => {});
    await openPage();

    await userEvent.click(screen.getByRole('button', { name: /Descargar corte de Antioquia/ }));

    await waitFor(() => expect(download).toHaveBeenCalled());
    expect(requestedPath).toBe(`/api/v1/admin/reports/cuts/${OLD_CUT.id}/excel`);
    expect(download.mock.calls[0][0]).toBe(`corte-cobertura-${OLD_CUT.id}.xlsx`);
  });

  it('hasNoAxeViolations', async () => {
    serve();
    await openPage();

    const options = { rules: { 'color-contrast': { enabled: false } } };
    expect((await axe.run(document.body, options)).violations).toEqual([]);
  });
});
