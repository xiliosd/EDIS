import { READ_ONLY_NOTE } from '../../components/ReadOnlyNotice';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { readOnlyEnvironment, server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';
import type { HistoryView } from './history-api';

const HISTORY_URL = '/admin/cuestionario/EDIN/2027/historial';
const HISTORY_API = '*/api/v1/admin/questionnaire/EDIN/2027/history';
const PROBLEM = { 'Content-Type': 'application/problem+json' };

function loginAsAdmin() {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({
        accountType: 'ADMINISTRATOR',
        roles: ['ADMIN_CUESTIONARIO'],
        departmentCode: '11',
      }),
    ),
    http.get('*/api/v1/auth/csrf', () => HttpResponse.json({ token: 'x' })),
  );
}

function history(state: 'borrador' | 'publicado'): HistoryView {
  return {
    version: 3,
    state,
    entries: [
      {
        version: 3,
        savedAt: '2026-11-03T15:00:00Z',
        user: 'ana.prueba',
        operation: 'editar-salto',
        reason: 'Ajuste del destino del salto',
        current: true,
      },
      {
        version: 2,
        savedAt: '2026-11-02T15:00:00Z',
        user: 'luis.prueba',
        operation: 'agregar-salto',
        current: false,
      },
      {
        version: 1,
        savedAt: '2026-11-01T15:00:00Z',
        user: 'ana.prueba',
        operation: 'crear-anio',
        current: false,
      },
    ],
  };
}

const rulesOf = (value: string) => ({
  saltos: [
    { id: 's1', si: { pregunta: 'C01', op: 'eq', valor: value }, entonces: { preguntas: ['C02'] } },
  ],
});

function serve(state: 'borrador' | 'publicado') {
  loginAsAdmin();
  server.use(
    http.get(HISTORY_API, () => HttpResponse.json(history(state))),
    http.get(`${HISTORY_API}/:version`, ({ params }) =>
      HttpResponse.json(rulesOf(params.version === '3' ? '2' : '1')),
    ),
  );
}

async function openPage() {
  const app = renderApp(HISTORY_URL);
  await screen.findByRole('heading', { name: 'Historial EDIN 2027' });
  await screen.findByRole('table', { name: 'Versiones guardadas' });
  return app;
}

describe('HistoryPage', () => {
  it('lista las versiones con sus columnas y marca la actual', async () => {
    serve('borrador');
    await openPage();

    const table = screen.getByRole('table', { name: 'Versiones guardadas' });
    for (const header of ['Versión', 'Fecha', 'Usuario', 'Operación', 'Motivo', 'Actual']) {
      expect(within(table).getByRole('columnheader', { name: header })).toBeInTheDocument();
    }
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4);
    expect(rows[1]).toHaveTextContent('ana.prueba');
    expect(rows[1]).toHaveTextContent('Ajuste del destino del salto');
    expect(rows[1]).toHaveTextContent('Sí');
  });

  it('compara por defecto la versión anterior con la actual, de la más vieja a la más nueva', async () => {
    serve('borrador');
    await openPage();

    const diff = await screen.findByRole('region', { name: 'Diferencias entre versiones' });
    expect(await within(diff).findByText('Si C01 = 1 → no se muestra C02')).toBeInTheDocument();
    expect(within(diff).getByText('Si C01 = 2 → no se muestra C02')).toBeInTheDocument();
    expect(within(diff).getByText('cambiada')).toBeInTheDocument();
  });

  it('muestra la misma comparación aunque se elijan las versiones en orden inverso', async () => {
    serve('borrador');
    await openPage();
    const user = userEvent.setup();

    await user.selectOptions(screen.getByLabelText('Primera versión a comparar'), '3');
    await user.selectOptions(screen.getByLabelText('Segunda versión a comparar'), '2');

    const diff = screen.getByRole('region', { name: 'Diferencias entre versiones' });
    expect(await within(diff).findByText('Si C01 = 2 → no se muestra C02')).toBeInTheDocument();
    expect(diff).toHaveTextContent('Versión 2 → versión 3');
  });

  it('deshabilita Restaurar en un año publicado', async () => {
    serve('publicado');
    await openPage();

    const buttons = screen.getAllByRole('button', { name: /^Restaurar esta versión/ });
    expect(buttons.length).toBeGreaterThan(0);
    for (const button of buttons) {
      expect(button).toBeDisabled();
    }
    expect(screen.getByText(/solo se puede restaurar en un borrador/i)).toBeInTheDocument();
  });

  it('confirma con la advertencia de las tablas FRM_* y restaura con la versión leída', async () => {
    serve('borrador');
    let body: unknown;
    let restored = '';
    server.use(
      http.post(`${HISTORY_API}/:version/restore`, async ({ request, params }) => {
        body = await request.json();
        restored = String(params.version);
        return HttpResponse.json({ version: 4, report: { errors: [], warnings: [] } });
      }),
    );
    await openPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Restaurar esta versión 2' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('Los textos y preguntas (tablas FRM_*) no se restauran.');
    await user.click(within(dialog).getByRole('button', { name: 'Restaurar' }));

    await waitFor(() => expect(body).toEqual({ version: 3 }));
    expect(restored).toBe('2');
    expect(
      await screen.findByText('Se restauró la versión 2 como versión nueva.'),
    ).toBeInTheDocument();
  });

  it('muestra el resumen del análisis cuando la restauración agrega errores (422)', async () => {
    serve('borrador');
    server.use(
      http.post(`${HISTORY_API}/:version/restore`, () =>
        HttpResponse.json(
          {
            title: 'La restauración agrega errores',
            detail: 'La versión elegida agrega errores de análisis.',
            report: {
              errors: [{ code: 'CICLO', element: 'regla:s1', message: 'Hay un ciclo de saltos.' }],
              warnings: [],
            },
          },
          { status: 422, headers: PROBLEM },
        ),
      ),
    );
    await openPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Restaurar esta versión 1' }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Restaurar' }),
    );

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Hay un ciclo de saltos.');
    await waitFor(() => expect(alert).toHaveFocus());
  });
});

describe('HistoryPage en un ambiente de solo lectura', () => {
  it('compara versiones pero deshabilita Restaurar y lo enlaza al aviso', async () => {
    readOnlyEnvironment();
    serve('borrador');
    await openPage();

    expect(await screen.findByRole('note')).toHaveTextContent(READ_ONLY_NOTE);
    const buttons = screen.getAllByRole('button', { name: /^Restaurar esta versión/ });
    expect(buttons.length).toBeGreaterThan(0);
    for (const button of buttons) {
      expect(button).toBeDisabled();
      expect(button).toHaveAccessibleDescription(READ_ONLY_NOTE);
    }
  });
});
