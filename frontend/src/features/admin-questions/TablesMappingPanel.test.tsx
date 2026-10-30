import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw-server';
import {
  API_BASE,
  draftView,
  PAGE_C1,
  PAGE_H1,
  problem,
  serveTree,
  TREE_URL,
} from '../../test/questionnaire-fixtures';
import { renderApp } from '../../test/render-app';

afterEach(() => {
  vi.restoreAllMocks();
});

function panel() {
  return screen.getByRole('region', { name: 'Tablas por capítulo' });
}

describe('TablesMappingPanel', () => {
  it('tablesPanelFlagsMissing', async () => {
    serveTree();
    server.use(
      http.get(`${API_BASE}/tables/H/create-script`, () =>
        HttpResponse.text('CREATE TABLE EDIN_27_CAP_H AS SELECT * FROM EDIN_20_CAP_H WHERE 1 = 0;'),
      ),
    );
    Object.defineProperty(URL, 'createObjectURL', {
      value: vi.fn(() => 'blob:x'),
      configurable: true,
    });
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    renderApp(TREE_URL);
    const user = userEvent.setup();

    await screen.findByRole('heading', { name: 'Tablas por capítulo' });
    expect(within(panel()).getByLabelText('Tabla del capítulo C')).toHaveValue('EDIN_27_CAP_C');
    expect(within(panel()).getByLabelText('Tabla del capítulo H')).toHaveValue('EDIN_27_CAP_H');
    const missingRow = within(panel()).getByLabelText('Tabla del capítulo H').closest('li')!;
    expect(within(missingRow).getByText('Tabla faltante')).toBeInTheDocument();
    const presentRow = within(panel()).getByLabelText('Tabla del capítulo C').closest('li')!;
    expect(within(presentRow).queryByText('Tabla faltante')).toBeNull();
    expect(
      within(presentRow).queryByRole('button', { name: /Descargar script CREATE TABLE/ }),
    ).toBeNull();

    await user.click(
      within(missingRow).getByRole('button', { name: 'Descargar script CREATE TABLE de H' }),
    );
    await vi.waitFor(() => expect(click).toHaveBeenCalled());
  });

  it('savesMappingWithVersionAndReloads', async () => {
    let body: unknown;
    serveTree();
    server.use(
      http.put(`${API_BASE}/tables`, async ({ request }) => {
        body = await request.json();
        server.use(
          http.get(API_BASE, () =>
            HttpResponse.json(
              draftView({
                version: 5,
                tables: { C: 'EDIN_27_CAP_C', H: 'EDIN_20_CAP_H' },
                pages: [PAGE_C1, { ...PAGE_H1, tableName: 'EDIN_20_CAP_H', tableMissing: false }],
              }),
            ),
          ),
        );
        return HttpResponse.json({
          version: 5,
          tables: { C: 'EDIN_27_CAP_C', H: 'EDIN_20_CAP_H' },
          missing: [],
        });
      }),
    );
    renderApp(TREE_URL);
    const user = userEvent.setup();

    const field = await screen.findByLabelText('Tabla del capítulo H');
    await user.clear(field);
    await user.type(field, 'EDIN_20_CAP_H');
    await user.click(within(panel()).getByRole('button', { name: 'Guardar tablas' }));

    expect(await screen.findByText('Tablas guardadas.')).toBeInTheDocument();
    expect(body).toEqual({ tables: { C: 'EDIN_27_CAP_C', H: 'EDIN_20_CAP_H' }, version: 4 });
    await vi.waitFor(() => expect(within(panel()).queryByText('Tabla faltante')).toBeNull());
    expect(screen.getByText('Versión 5')).toBeInTheDocument();
  });

  it('showsInvalidTableError', async () => {
    serveTree();
    server.use(
      http.put(`${API_BASE}/tables`, () =>
        problem(422, 'La tabla indicada no tiene las columnas C_C, ESTADO y TC.', {
          'tables.C': 'La tabla indicada no tiene las columnas C_C, ESTADO y TC.',
        }),
      ),
    );
    renderApp(TREE_URL);
    const user = userEvent.setup();

    const field = await screen.findByLabelText('Tabla del capítulo C');
    await user.clear(field);
    await user.type(field, 'DUAL');
    await user.click(within(panel()).getByRole('button', { name: 'Guardar tablas' }));

    expect(
      await within(panel()).findByText('La tabla indicada no tiene las columnas C_C, ESTADO y TC.'),
    ).toBeInTheDocument();
    expect(field).toHaveAttribute('aria-invalid', 'true');
  });
});
