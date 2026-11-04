import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw-server';
import { LONG_FLOW_TIMEOUT } from '../../test/questionnaire-fixtures';
import { renderApp } from '../../test/render-app';
import { RULES_API, rulesView } from '../../test/rules-fixtures';
import { SIMULATOR_API, SIMULATOR_URL, simulatorView } from '../../test/simulator-fixtures';
import type { CaseResult, RulesEditorView, TestCase } from '../admin-rules/rules-api';

const PROBLEM = { 'Content-Type': 'application/problem+json' };

const CASES: TestCase[] = [
  {
    id: 'caso-a',
    nombre: 'Pasa en C1',
    pagina: 'C1',
    respuestas: { C01A: '1' },
    esperado: { ocultas: [], errores: {} },
  },
  {
    id: 'caso-b',
    nombre: 'Falla en C1',
    pagina: 'C1',
    respuestas: { C01A: '2', C03: '1' },
    esperado: { ocultas: [], errores: {} },
  },
  {
    id: 'caso-c',
    nombre: 'Revisar en C2',
    pagina: 'C2',
    respuestas: { C11: '5' },
    esperado: { ocultas: [], errores: {} },
    porRevisar: true,
  },
];
const RESULTS: CaseResult[] = [
  { id: 'caso-a', status: 'pasa', differences: [] },
  { id: 'caso-b', status: 'falla', differences: ['Se esperaba visible C01B y está oculta.'] },
  { id: 'caso-c', status: 'por_revisar', differences: [] },
];

function loginAsAdmin() {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({
        accountType: 'ADMINISTRATOR',
        roles: ['ADMIN_CUESTIONARIO'],
        departmentCode: '11',
      }),
    ),
  );
}

function serve(view: RulesEditorView = rulesView()) {
  server.use(
    http.get(SIMULATOR_API, () => HttpResponse.json(simulatorView())),
    http.get(RULES_API, () => HttpResponse.json(view)),
  );
}

async function openSimulator(view?: RulesEditorView, url = SIMULATOR_URL) {
  serve(view);
  const app = renderApp(url);
  await screen.findByRole('heading', { name: 'Simulador EDIN 2027' });
  await screen.findByRole('radiogroup', { name: /C01A/ });
  return app;
}

function group(column: string): HTMLElement {
  return screen.getByRole('radiogroup', { name: new RegExp(column) });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token-de-prueba';
  loginAsAdmin();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('SimulatorPage', () => {
  it(
    'una pregunta oculta sigue visible, deshabilitada, con la regla que la oculta',
    async () => {
      await openSimulator();
      const user = userEvent.setup();

      await user.click(within(group('C01A')).getByLabelText('No'));

      expect(
        await screen.findByText('Oculta por la regla c01a-no: Si C01A = No → no se muestra C01B'),
      ).toBeVisible();
      expect(within(group('C01B')).getByLabelText('Sí')).toBeDisabled();
      expect(within(group('C01A')).getByLabelText('Sí')).toBeEnabled();
    },
    LONG_FLOW_TIMEOUT,
  );

  it('muestra los errores del encuestado con los mismos mensajes', async () => {
    await openSimulator();

    expect(screen.getAllByText('Responda esta pregunta.').length).toBeGreaterThan(0);
  });

  it('las respuestas sobreviven al cambiar de página', async () => {
    await openSimulator();
    const user = userEvent.setup();

    await user.click(within(group('C01A')).getByLabelText('Sí'));
    await user.selectOptions(screen.getByLabelText('Página'), 'C2');
    await user.type(await screen.findByLabelText(/C11/), '7');
    await user.selectOptions(screen.getByLabelText('Página'), 'C1');

    expect(
      within(await screen.findByRole('radiogroup', { name: /C01A/ })).getByLabelText('Sí'),
    ).toBeChecked();
    await user.selectOptions(screen.getByLabelText('Página'), 'C2');
    expect(await screen.findByLabelText(/C11/)).toHaveValue('7');
  });

  it('Reiniciar respuestas lo borra todo', async () => {
    await openSimulator();
    const user = userEvent.setup();
    await user.click(within(group('C01A')).getByLabelText('No'));

    await user.click(screen.getByRole('button', { name: 'Reiniciar respuestas' }));

    expect(within(group('C01A')).getByLabelText('No')).not.toBeChecked();
    expect(screen.queryByText(/Oculta por la regla/)).not.toBeInTheDocument();
  });

  it(
    'guarda el caso con el cuerpo exacto y no escribe en localStorage',
    async () => {
      const setItem = vi.spyOn(Storage.prototype, 'setItem');
      let body: unknown;
      server.use(
        http.put(`${RULES_API}/cases/sin-trabajo`, async ({ request }) => {
          body = await request.json();
          return HttpResponse.json({
            version: 5,
            report: { errors: [], warnings: [] },
            caseResults: [{ id: 'sin-trabajo', status: 'pasa', differences: [] }],
          });
        }),
      );
      await openSimulator();
      const user = userEvent.setup();

      await user.click(within(group('C01A')).getByLabelText('No'));
      await user.click(screen.getByRole('button', { name: 'Guardar como caso de prueba' }));
      await user.type(screen.getByLabelText('Identificador del caso'), 'sin-trabajo');
      await user.type(screen.getByLabelText('Nombre del caso'), 'No trabaja');
      await user.click(screen.getByRole('button', { name: 'Guardar caso' }));

      expect(await screen.findByText('Caso sin-trabajo guardado.')).toBeInTheDocument();
      expect(body).toEqual({
        testCase: {
          id: 'sin-trabajo',
          nombre: 'No trabaja',
          pagina: 'C1',
          respuestas: { C01A: '2', C03: '0' },
          esperado: { ocultas: ['C01B'], errores: {} },
        },
        version: 4,
      });
      expect(screen.getByRole('button', { name: 'Guardar como caso de prueba' })).toHaveFocus();
      expect(setItem).not.toHaveBeenCalled();
    },
    LONG_FLOW_TIMEOUT,
  );

  it('un 409 al guardar el caso pide recargar', async () => {
    server.use(
      http.put(`${RULES_API}/cases/otro`, () =>
        HttpResponse.json(
          {
            title: 'Conflicto',
            detail: 'Otro usuario modificó el cuestionario. Recargue la página.',
          },
          { status: 409, headers: PROBLEM },
        ),
      ),
    );
    await openSimulator();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Guardar como caso de prueba' }));
    await user.type(screen.getByLabelText('Identificador del caso'), 'otro');
    await user.type(screen.getByLabelText('Nombre del caso'), 'Otro');
    await user.click(screen.getByRole('button', { name: 'Guardar caso' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Otro usuario modificó el cuestionario. Recargue la página.');
    expect(within(alert).getByRole('button', { name: 'Recargar' })).toBeVisible();
    // Lo escrito espera al reintento.
    await user.click(screen.getByRole('button', { name: 'Guardar como caso de prueba' }));
    expect(screen.getByLabelText('Identificador del caso')).toHaveValue('otro');
    expect(screen.getByLabelText('Nombre del caso')).toHaveValue('Otro');
  });

  it(
    'el borrador de un guardado fallido se descarta al reiniciar o al abrir otro caso',
    async () => {
      server.use(
        http.put(`${RULES_API}/cases/:id`, () =>
          HttpResponse.json(
            { title: 'Conflicto', detail: 'Otro usuario modificó el cuestionario.' },
            { status: 409, headers: PROBLEM },
          ),
        ),
      );
      await openSimulator(rulesView({ cases: CASES, caseResults: RESULTS }));
      const user = userEvent.setup();
      const saveButton = () => screen.getByRole('button', { name: 'Guardar como caso de prueba' });
      async function failToSave() {
        await user.click(saveButton());
        await user.type(screen.getByLabelText('Identificador del caso'), 'otro');
        await user.type(screen.getByLabelText('Nombre del caso'), 'Otro');
        await user.click(screen.getByRole('button', { name: 'Guardar caso' }));
        await screen.findByText('Otro usuario modificó el cuestionario.');
      }

      await failToSave();
      await user.click(screen.getByRole('button', { name: 'Reiniciar respuestas' }));
      await user.click(saveButton());
      expect(screen.getByLabelText('Identificador del caso')).toHaveValue('');
      await user.click(screen.getByRole('button', { name: 'Cancelar' }));

      await failToSave();
      await user.click(
        within(document.getElementById('caso-caso-a')!).getByRole('button', {
          name: /Abrir en el simulador/,
        }),
      );
      await user.click(saveButton());
      expect(screen.getByLabelText('Identificador del caso')).toHaveValue('');
      expect(screen.getByLabelText('Nombre del caso')).toHaveValue('');
    },
    LONG_FLOW_TIMEOUT,
  );

  it(
    'lista los casos con su estado y "Abrir en el simulador" restaura respuestas y página',
    async () => {
      await openSimulator(rulesView({ cases: CASES, caseResults: RESULTS }));
      const user = userEvent.setup();
      const row = (id: string) => document.getElementById(`caso-${id}`)!;

      expect(within(row('caso-a')).getByText('Pasa')).toBeVisible();
      expect(within(row('caso-b')).getByText('Falla')).toBeVisible();
      expect(
        within(row('caso-b')).getByText('Se esperaba visible C01B y está oculta.'),
      ).toBeVisible();
      expect(within(row('caso-c')).getByText('Por revisar')).toBeVisible();

      await user.click(
        within(row('caso-c')).getByRole('button', { name: /Abrir en el simulador/ }),
      );
      expect(screen.getByLabelText('Página')).toHaveValue('C2');
      expect(await screen.findByLabelText(/C11/)).toHaveValue('5');

      await user.click(
        within(row('caso-b')).getByRole('button', { name: /Abrir en el simulador/ }),
      );
      expect(screen.getByLabelText('Página')).toHaveValue('C1');
      expect(within(group('C01A')).getByLabelText('No')).toBeChecked();
      expect(await screen.findByText(/Oculta por la regla c01a-no/)).toBeVisible();
    },
    LONG_FLOW_TIMEOUT,
  );

  it(
    'elimina un caso tras confirmar, con la versión leída',
    async () => {
      let version: string | null = null;
      server.use(
        http.delete(`${RULES_API}/cases/caso-a`, ({ request }) => {
          version = new URL(request.url).searchParams.get('version');
          return HttpResponse.json({
            version: 5,
            report: { errors: [], warnings: [] },
            caseResults: [],
          });
        }),
      );
      await openSimulator(rulesView({ cases: CASES, caseResults: RESULTS }));
      const user = userEvent.setup();

      await user.click(screen.getByRole('button', { name: 'Eliminar caso caso-a' }));
      await user.click(screen.getByRole('button', { name: 'Eliminar' }));

      expect(await screen.findByText('Caso caso-a eliminado.')).toBeInTheDocument();
      expect(version).toBe('4');
      await waitFor(() => expect(document.getElementById('caso-caso-a')).toBeNull());
    },
    LONG_FLOW_TIMEOUT,
  );

  it('en un año publicado el simulador funciona pero no guarda ni elimina casos', async () => {
    await openSimulator(rulesView({ state: 'publicado', cases: CASES, caseResults: RESULTS }));
    const user = userEvent.setup();

    expect(screen.getByRole('button', { name: 'Guardar como caso de prueba' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Eliminar caso caso-a' })).toBeDisabled();
    expect(screen.getByText(/está publicado/)).toBeVisible();
    await user.click(within(group('C01A')).getByLabelText('No'));
    expect(await screen.findByText(/Oculta por la regla c01a-no/)).toBeVisible();
  });

  it(
    'con #caso-<id> desplaza la vista y enfoca la fila del caso',
    async () => {
      await openSimulator(
        rulesView({ cases: CASES, caseResults: RESULTS }),
        `${SIMULATOR_URL}#caso-caso-b`,
      );

      await waitFor(() => expect(document.getElementById('caso-caso-b')).toHaveFocus());
    },
    LONG_FLOW_TIMEOUT,
  );

  it('si las reglas cambiaron no guarda: refresca el simulador y avisa', async () => {
    let simulatorReads = 0;
    let puts = 0;
    server.use(
      http.get(SIMULATOR_API, () => {
        simulatorReads += 1;
        return HttpResponse.json(simulatorView());
      }),
      http.get(RULES_API, () => HttpResponse.json(rulesView({ version: 5 }))),
      http.put(`${RULES_API}/cases/:id`, () => {
        puts += 1;
        return HttpResponse.json({ version: 6 });
      }),
    );
    renderApp(SIMULATOR_URL);
    await screen.findByRole('radiogroup', { name: /C01A/ });
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Guardar como caso de prueba' }));
    await user.type(screen.getByLabelText('Identificador del caso'), 'nuevo');
    await user.type(screen.getByLabelText('Nombre del caso'), 'Nuevo');
    await user.click(screen.getByRole('button', { name: 'Guardar caso' }));

    expect(
      await screen.findByText('Las reglas cambiaron; revise el resultado antes de guardar.'),
    ).toBeInTheDocument();
    expect(puts).toBe(0);
    await waitFor(() => expect(simulatorReads).toBeGreaterThanOrEqual(2));
    // Lo escrito en el diálogo no se pierde: al volver a abrirlo sigue ahí.
    await user.click(screen.getByRole('button', { name: 'Guardar como caso de prueba' }));
    expect(screen.getByLabelText('Identificador del caso')).toHaveValue('nuevo');
    expect(screen.getByLabelText('Nombre del caso')).toHaveValue('Nuevo');
  });

  it(
    'dos casos guardados seguidos envían la versión nueva',
    async () => {
      let current = 4;
      const sent: number[] = [];
      server.use(
        http.put(`${RULES_API}/cases/:id`, async ({ request }) => {
          const body = (await request.json()) as { version: number };
          sent.push(body.version);
          current += 1;
          return HttpResponse.json({ version: current, report: { errors: [], warnings: [] } });
        }),
      );
      await openSimulator();
      const user = userEvent.setup();

      for (const id of ['primero', 'segundo']) {
        await user.click(screen.getByRole('button', { name: 'Guardar como caso de prueba' }));
        await user.type(screen.getByLabelText('Identificador del caso'), id);
        await user.type(screen.getByLabelText('Nombre del caso'), id);
        await user.click(screen.getByRole('button', { name: 'Guardar caso' }));
        expect(await screen.findByText(`Caso ${id} guardado.`)).toBeInTheDocument();
      }

      expect(sent).toEqual([4, 5]);
      expect(
        screen.queryByText('Las reglas cambiaron; revise el resultado antes de guardar.'),
      ).toBeNull();
    },
    LONG_FLOW_TIMEOUT,
  );
});
