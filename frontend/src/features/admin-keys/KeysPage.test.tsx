import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as downloadFile from '../../services/download-file';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';

const KEYS_URL = '/admin/claves';
const SUMMARY_API = '*/api/v1/admin/keys/summary';
const GENERATE_API = '*/api/v1/admin/keys/generate';
const EXCEL_API = '*/api/v1/admin/keys/excel';
const PROBLEM = { 'Content-Type': 'application/problem+json' };
const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

const WITH_PENDING = {
  year: 2026,
  departments: [
    { code: 5, name: 'Antioquia', eligible: 1200, withKey: 1000, withoutKey: 200 },
    { code: 8, name: 'Atlántico', eligible: 50, withKey: 20, withoutKey: 30 },
  ],
};

const NO_PENDING = {
  year: 2026,
  departments: [
    { code: 5, name: 'Antioquia', eligible: 1200, withKey: 1200, withoutKey: 0 },
    { code: 8, name: 'Atlántico', eligible: 50, withKey: 50, withoutKey: 0 },
  ],
};

function serve(summary: object = WITH_PENDING, roles = ['ADMIN_NACIONAL']) {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({ accountType: 'ADMINISTRATOR', roles, departmentCode: '5' }),
    ),
    http.get('*/api/v1/auth/csrf', () => HttpResponse.json({ token: 'x' })),
    http.get('*/api/v1/admin/reports/years', () => HttpResponse.json([2025, 2026])),
    http.get(SUMMARY_API, () => HttpResponse.json(summary)),
  );
}

async function openPage() {
  const app = renderApp(KEYS_URL);
  await screen.findByRole('heading', { name: 'Claves de la muestra' });
  await screen.findByRole('table', { name: 'Claves de la muestra por departamento' });
  return app;
}

afterEach(() => vi.restoreAllMocks());

describe('KeysPage', () => {
  it('showsTheSummaryByDepartmentWithTotals', async () => {
    serve();
    await openPage();

    const antioquia = screen.getByRole('row', { name: /Antioquia/ });
    expect(within(antioquia).getByText('1.200')).toBeInTheDocument();
    expect(within(antioquia).getByText('200')).toBeInTheDocument();
    const total = screen.getByRole('row', { name: /Total/ });
    expect(within(total).getByText('1.250')).toBeInTheDocument();
    expect(within(total).getByText('1.020')).toBeInTheDocument();
    expect(within(total).getByText('230')).toBeInTheDocument();
  });

  it('asksForConfirmationWithTheCountAndSendsItAsExpectedWithoutKey', async () => {
    serve();
    let body: unknown;
    server.use(
      http.post(GENERATE_API, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ generated: 230 });
      }),
    );
    await openPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Generar claves' }));

    const dialog = await screen.findByRole('dialog', { name: 'Generar claves' });
    expect(dialog).toHaveTextContent(
      'Se generarán 230 claves para los funcionarios web sin clave del año 2026. ' +
        'Las claves existentes no cambian.',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Generar' }));

    await waitFor(() => expect(body).toEqual({ anio: 2026, expectedWithoutKey: 230 }));
  });

  it('reloadsTheSummaryAndAnnouncesTheResultAfterGenerating', async () => {
    serve();
    let generated = false;
    server.use(
      http.get(SUMMARY_API, () => HttpResponse.json(generated ? NO_PENDING : WITH_PENDING)),
      http.post(GENERATE_API, () => {
        generated = true;
        return HttpResponse.json({ generated: 230 });
      }),
    );
    await openPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Generar claves' }));
    const dialog = await screen.findByRole('dialog', { name: 'Generar claves' });
    await user.click(within(dialog).getByRole('button', { name: 'Generar' }));

    const status = await screen.findByText('Se generaron 230 claves.');
    expect(status).toHaveAttribute('role', 'status');
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Generar claves' })).toBeDisabled(),
    );
    expect(status).toHaveFocus();
    // Las claves nunca se ven: la pantalla solo tiene cifras y nombres de departamento.
    expect(document.body).not.toHaveTextContent(/\b[A-Z]{8}\b/);
  });

  it('disablesGeneratingWithAVisibleExplanationWhenNothingIsPending', async () => {
    serve(NO_PENDING);
    await openPage();

    const button = screen.getByRole('button', { name: 'Generar claves' });
    expect(button).toBeDisabled();
    const explanation = screen.getByText(/No hay funcionarios web sin clave/);
    expect(explanation).toBeVisible();
    expect(button).toHaveAccessibleDescription(explanation.textContent ?? '');
  });

  it('showsTheConflictNoticeAndReloadsTheSummaryOn409', async () => {
    serve();
    let summaryCalls = 0;
    server.use(
      http.get(SUMMARY_API, () => {
        summaryCalls += 1;
        return HttpResponse.json(summaryCalls === 1 ? WITH_PENDING : NO_PENDING);
      }),
      http.post(GENERATE_API, () =>
        HttpResponse.json(
          { title: 'Conflicto', status: 409, detail: 'Cambió.' },
          { status: 409, headers: PROBLEM },
        ),
      ),
    );
    await openPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Generar claves' }));
    const dialog = await screen.findByRole('dialog', { name: 'Generar claves' });
    await user.click(within(dialog).getByRole('button', { name: 'Generar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El número de funcionarios sin clave cambió',
    );
    await waitFor(() => expect(summaryCalls).toBe(2));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Generar claves' })).toBeDisabled(),
    );
    expect(screen.queryByText(/Se generaron/)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Recargar' }));
    await waitFor(() => expect(summaryCalls).toBe(3));
  });

  it('showsTheServerErrorWhenGeneratingFails', async () => {
    serve();
    server.use(
      http.post(GENERATE_API, () =>
        HttpResponse.json(
          { title: 'Prohibido', status: 403, detail: 'No puede generar claves.' },
          { status: 403, headers: PROBLEM },
        ),
      ),
    );
    await openPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Generar claves' }));
    const dialog = await screen.findByRole('dialog', { name: 'Generar claves' });
    await user.click(within(dialog).getByRole('button', { name: 'Generar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No puede generar claves.');
  });

  it('cancelsTheConfirmationWithoutSending', async () => {
    serve();
    let posted = false;
    server.use(
      http.post(GENERATE_API, () => {
        posted = true;
        return HttpResponse.json({ generated: 0 });
      }),
    );
    await openPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Generar claves' }));
    const dialog = await screen.findByRole('dialog', { name: 'Generar claves' });
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    expect(posted).toBe(false);
    expect(screen.queryByText(/Se generaron/)).not.toBeInTheDocument();
  });

  it('downloadsTheExcelOfADepartmentWithTheServerName', async () => {
    serve();
    let query: URLSearchParams | undefined;
    server.use(
      http.get(EXCEL_API, ({ request }) => {
        query = new URL(request.url).searchParams;
        return new HttpResponse(new Uint8Array([80, 75]), {
          headers: {
            'Content-Type': XLSX,
            'Content-Disposition': 'attachment; filename="claves-general-2026-dpto08.xlsx"',
          },
        });
      }),
    );
    const download = vi.spyOn(downloadFile, 'downloadBlobFile').mockImplementation(() => {});
    await openPage();

    await userEvent.click(
      screen.getByRole('button', { name: 'Descargar Excel de claves de Atlántico' }),
    );

    await waitFor(() => expect(download).toHaveBeenCalled());
    expect(download.mock.calls[0][0]).toBe('claves-general-2026-dpto08.xlsx');
    expect(query?.get('departamento')).toBe('8');
    expect(query?.get('anio')).toBe('2026');
  });

  it('showsAnErrorWhenTheDepartmentHasNoEligibleOfficials', async () => {
    serve();
    server.use(
      http.get(EXCEL_API, () =>
        HttpResponse.json(
          { title: 'No encontrado', status: 404, detail: 'No hay funcionarios elegibles.' },
          { status: 404, headers: PROBLEM },
        ),
      ),
    );
    const download = vi.spyOn(downloadFile, 'downloadBlobFile').mockImplementation(() => {});
    await openPage();

    await userEvent.click(
      screen.getByRole('button', { name: 'Descargar Excel de claves de Antioquia' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('No hay funcionarios elegibles.');
    expect(download).not.toHaveBeenCalled();
  });

  it('reloadsTheSummaryWhenTheYearChanges', async () => {
    serve();
    const requested: Array<string | null> = [];
    server.use(
      http.get(SUMMARY_API, ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('anio'));
        return HttpResponse.json(WITH_PENDING);
      }),
    );
    await openPage();

    await userEvent.selectOptions(screen.getByLabelText('Año'), '2025');

    await waitFor(() => expect(requested).toContain('2025'));
  });

  it('showsAnErrorWithRetryWhenTheSummaryFails', async () => {
    serve();
    let calls = 0;
    server.use(
      http.get(SUMMARY_API, () => {
        calls += 1;
        return calls === 1
          ? HttpResponse.json(
              { title: 'Error', status: 500, detail: 'Falló el resumen.' },
              { status: 500, headers: PROBLEM },
            )
          : HttpResponse.json(WITH_PENDING);
      }),
    );
    renderApp(KEYS_URL);

    expect(await screen.findByRole('alert')).toHaveTextContent('Falló el resumen.');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
  });

  it('isNotAvailableForOtherRoles', async () => {
    serve(WITH_PENDING, ['ADMIN_TERRITORIAL']);
    renderApp(KEYS_URL);

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Claves de la muestra' })).toBeNull(),
    );
  });

  it('hasNoAccessibilityViolationsInThePageAndTheDialog', async () => {
    serve();
    await openPage();
    const options = { rules: { 'color-contrast': { enabled: false } } };
    expect((await axe.run(document.body, options)).violations).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: 'Generar claves' }));
    await screen.findByRole('dialog', { name: 'Generar claves' });
    expect((await axe.run(document.body, options)).violations).toEqual([]);
  });
});
