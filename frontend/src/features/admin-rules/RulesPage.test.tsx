import { READ_ONLY_NOTE } from '../../components/ReadOnlyNotice';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { readOnlyEnvironment, server } from '../../test/msw-server';
import { LONG_FLOW_TIMEOUT } from '../../test/questionnaire-fixtures';
import { renderApp } from '../../test/render-app';
import { RULES_API, RULES_URL, rulesView } from '../../test/rules-fixtures';
import { simulatorQueryKey, type RulesEditorView } from './rules-api';

const EXPORT_TEXT = '{\n  "encuesta": "EDIN",\n  "anio": 2027\n}';
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
  );
}

function serveRules(view: RulesEditorView = rulesView()) {
  server.use(
    http.get(RULES_API, () => HttpResponse.json(view)),
    http.get(`${RULES_API}/export`, () => HttpResponse.text(EXPORT_TEXT)),
  );
}

async function openRules(view?: RulesEditorView) {
  serveRules(view);
  renderApp(RULES_URL);
  await screen.findByRole('heading', { name: 'Reglas EDIN 2027' });
  await screen.findByText('Si C01A = No → no se muestra C01B');
}

function rowOf(ruleId: string): HTMLElement {
  return document.getElementById(`regla-${ruleId}`)!;
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token-de-prueba';
  loginAsAdmin();
});

describe('RulesPage', () => {
  it(
    'lista las reglas de la primera página con su frase y botones',
    async () => {
      await openRules();

      expect(screen.getByLabelText('Página')).toHaveValue('C1');
      const row = rowOf('c01a-no');
      expect(within(row).getByText('c01a-no')).toBeVisible();
      expect(within(row).getByRole('button', { name: 'Editar regla c01a-no' })).toBeEnabled();
      expect(within(row).getByRole('button', { name: 'Eliminar regla c01a-no' })).toBeEnabled();
      // Las reglas de otras páginas no aparecen.
      expect(document.getElementById('regla-legacy-C09G')).toBeNull();
    },
    LONG_FLOW_TIMEOUT,
  );

  it('muestra "Regla incompleta" cuando falta la condición o el destino', async () => {
    await openRules();

    expect(within(rowOf('sin-destino')).getByText('Regla incompleta')).toBeVisible();
  });

  it('filtra por página y marca las reglas heredadas como solo lectura', async () => {
    await openRules();
    const user = userEvent.setup();

    await user.selectOptions(screen.getByLabelText('Página'), 'C2');

    const legacy = rowOf('legacy-C09G');
    expect(within(legacy).getByText('Heredada, solo lectura')).toBeVisible();
    expect(within(legacy).queryByRole('button')).not.toBeInTheDocument();
    expect(document.getElementById('regla-c01a-no')).toBeNull();
    expect(screen.getByRole('group', { name: 'Validación de C11' })).toBeVisible();
    expect(screen.queryByRole('group', { name: 'Validación de C01A' })).not.toBeInTheDocument();
  });

  it('resume el efecto en la página: preguntas ocultas por alguna regla', async () => {
    await openRules();

    const effect = screen.getByRole('region', { name: 'Efecto en la página' });
    const item = within(effect).getByText('C01B').closest('li')!;
    expect(item).toHaveTextContent('c01a-no');
    expect(item).toHaveTextContent('Si C01A = No → no se muestra C01B');
  });

  it('muestra los hallazgos junto al elemento que los causa', async () => {
    await openRules();

    expect(within(rowOf('c01a-no')).getByText('Reglas solapadas')).toBeVisible();
  });

  it('con el año publicado deshabilita todas las ediciones', async () => {
    await openRules(rulesView({ state: 'publicado' }));

    expect(screen.getByRole('textbox', { name: 'Motivo de la corrección' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Nueva regla' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Editar regla c01a-no' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Eliminar regla c01a-no' })).toBeDisabled();
    expect(screen.getByLabelText('Importar JSON')).toBeDisabled();
  });

  it('atiende el ancla #regla-<id> una sola vez y no secuestra el foco tras guardar', async () => {
    server.use(
      http.delete(`${RULES_API}/skips/c01a-no`, () =>
        HttpResponse.json({ version: 5, report: { errors: [], warnings: [] } }),
      ),
    );
    serveRules();
    renderApp(`${RULES_URL}#regla-anidada`);
    await screen.findByRole('heading', { name: 'Reglas EDIN 2027' });
    // El ancla lleva a la página de la regla y enfoca su fila.
    await waitFor(() => expect(rowOf('anidada')).toHaveFocus());
    expect(screen.getByLabelText('Página')).toHaveValue('C2');
    const user = userEvent.setup();

    await user.selectOptions(screen.getByLabelText('Página'), 'C1');
    await user.click(screen.getByRole('button', { name: 'Eliminar regla c01a-no' }));
    await user.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(await screen.findByText('Regla eliminada.')).toBeInTheDocument();
    // Tras guardar la versión cambia: el ancla ya atendida no vuelve a mover página ni foco.
    await waitFor(() => expect(document.getElementById('saltos-titulo')).toHaveFocus());
    expect(screen.getByLabelText('Página')).toHaveValue('C1');
  });

  it('en un año publicado elimina una regla solo con motivo y lo envía recortado', async () => {
    let url = '';
    let body: { motivo?: string } = {};
    server.use(
      http.delete(`${RULES_API}/skips/c01a-no`, async ({ request }) => {
        url = request.url;
        body = (await request.json()) as { motivo?: string };
        return HttpResponse.json({ version: 5, report: { errors: [], warnings: [] } });
      }),
    );
    await openRules(rulesView({ state: 'publicado' }));
    const user = userEvent.setup();

    await user.type(screen.getByRole('textbox', { name: 'Motivo de la corrección' }), '  corto ');
    expect(screen.getByRole('button', { name: 'Eliminar regla c01a-no' })).toBeDisabled();
    await user.type(
      screen.getByRole('textbox', { name: 'Motivo de la corrección' }),
      'pero ya suficiente  ',
    );
    await user.click(screen.getByRole('button', { name: 'Eliminar regla c01a-no' }));
    await user.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(await screen.findByText('Regla eliminada.')).toBeInTheDocument();
    expect(new URL(url).searchParams.get('version')).toBe('4');
    // El motivo va en el cuerpo: la URL, que queda en los logs de acceso, no lo lleva.
    expect(url).not.toContain('motivo');
    expect(body.motivo).toBe('corto pero ya suficiente');
    expect(screen.getByRole('textbox', { name: 'Motivo de la corrección' })).toHaveValue('');
  });

  it(
    'guarda una regla nueva con la versión leída y anuncia el resultado',
    async () => {
      let body: unknown;
      server.use(
        http.put(`${RULES_API}/skips/c01b-no`, async ({ request }) => {
          body = await request.json();
          return HttpResponse.json({ version: 5, report: { errors: [], warnings: [] } });
        }),
      );
      await openRules();
      const user = userEvent.setup();

      await user.click(screen.getByRole('button', { name: 'Nueva regla' }));
      await user.type(screen.getByLabelText('Identificador'), 'c01b-no');
      await user.selectOptions(screen.getByLabelText('Pregunta de la condición 1'), 'C01B');
      await user.selectOptions(screen.getByLabelText('Valor de la condición 1'), '2');
      await user.selectOptions(screen.getByLabelText('Preguntas que no se muestran'), 'C03');
      await user.click(screen.getByRole('button', { name: 'Guardar' }));

      expect(await screen.findByText('Regla guardada.')).toBeInTheDocument();
      expect(body).toEqual({
        rule: {
          id: 'c01b-no',
          si: { pregunta: 'C01B', op: 'eq', valor: '2' },
          entonces: { accion: 'deshabilitar', preguntas: ['C03'] },
        },
        version: 4,
      });
    },
    LONG_FLOW_TIMEOUT,
  );

  it('elimina una regla tras confirmar y envía la versión', async () => {
    let version: string | null = null;
    server.use(
      http.delete(`${RULES_API}/skips/c01a-no`, ({ request }) => {
        version = new URL(request.url).searchParams.get('version');
        return HttpResponse.json({ version: 5, report: { errors: [], warnings: [] } });
      }),
    );
    await openRules();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Eliminar regla c01a-no' }));
    await user.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(await screen.findByText('Regla eliminada.')).toBeInTheDocument();
    expect(version).toBe('4');
  });

  it(
    'un 422 muestra solo los errores nuevos, arriba y con el foco',
    async () => {
      server.use(
        http.put(`${RULES_API}/skips/c01a-no`, () =>
          HttpResponse.json(
            {
              title: 'Reglas inválidas',
              detail: 'Las reglas agregan errores.',
              report: {
                errors: [
                  {
                    code: 'CICLO',
                    element: 'regla:c01a-no',
                    questions: ['C01A', 'C01B'],
                    message: 'Hay un ciclo entre C01A y C01B.',
                  },
                  rulesView().report!.errors![0],
                ],
                warnings: [],
              },
            },
            { status: 422, headers: PROBLEM },
          ),
        ),
      );
      await openRules();
      const user = userEvent.setup();

      await user.click(screen.getByRole('button', { name: 'Editar regla c01a-no' }));
      await user.click(screen.getByRole('button', { name: 'Guardar' }));

      await screen.findAllByRole('alert');
      const alert = document.activeElement as HTMLElement;
      expect(alert).toHaveAttribute('role', 'alert');
      // El diálogo sigue abierto, con el borrador intacto, y la alerta está dentro de él.
      expect(within(screen.getByRole('dialog')).getByRole('alert')).toBe(alert);
      expect(screen.getByLabelText('Pregunta de la condición 1')).toHaveValue('C01A');
      expect(alert).toHaveTextContent('Ciclo de saltos');
      expect(alert).toHaveTextContent('Hay un ciclo entre C01A y C01B.');
      // El error que ya existía no es nuevo y no se repite en la alerta.
      expect(alert).not.toHaveTextContent('Rango inválido');
    },
    LONG_FLOW_TIMEOUT,
  );

  it(
    'un 422 al eliminar deja el foco en la alerta de la página, no en el botón de la regla',
    async () => {
      server.use(
        http.delete(`${RULES_API}/skips/c01a-no`, () =>
          HttpResponse.json(
            {
              title: 'Reglas inválidas',
              detail: 'Las reglas agregan errores.',
              report: {
                errors: [
                  {
                    code: 'CICLO',
                    element: 'regla:c01a-no',
                    questions: ['C01A', 'C01B'],
                    message: 'Hay un ciclo entre C01A y C01B.',
                  },
                ],
                warnings: [],
              },
            },
            { status: 422, headers: PROBLEM },
          ),
        ),
      );
      await openRules();
      const user = userEvent.setup();

      await user.click(screen.getByRole('button', { name: 'Eliminar regla c01a-no' }));
      await user.click(screen.getByRole('button', { name: 'Eliminar' }));

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent('Hay un ciclo entre C01A y C01B.');
      await waitFor(() => expect(alert).toHaveFocus());
    },
    LONG_FLOW_TIMEOUT,
  );

  it(
    'un 409 pide recargar',
    async () => {
      server.use(
        http.put(`${RULES_API}/skips/c01a-no`, () =>
          HttpResponse.json(
            {
              title: 'Conflicto',
              detail: 'Otro usuario modificó el cuestionario. Recargue la página.',
            },
            { status: 409, headers: PROBLEM },
          ),
        ),
      );
      await openRules();
      const user = userEvent.setup();

      await user.click(screen.getByRole('button', { name: 'Editar regla c01a-no' }));
      await user.click(screen.getByRole('button', { name: 'Guardar' }));

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent('Otro usuario modificó el cuestionario. Recargue la página.');
      expect(alert).toHaveFocus();
      expect(within(alert).getByRole('button', { name: 'Recargar' })).toBeVisible();
    },
    LONG_FLOW_TIMEOUT,
  );

  it('abre en solo lectura una regla anidada', async () => {
    await openRules();
    const user = userEvent.setup();

    await user.selectOptions(screen.getByLabelText('Página'), 'C2');
    await user.click(screen.getByRole('button', { name: 'Editar regla anidada' }));

    expect(screen.getByText('Esta regla solo se edita importando el archivo.')).toBeVisible();
  });

  it('muestra el JSON exportado y lo descarga', async () => {
    await openRules();

    expect(await screen.findByText(/"anio": 2027/)).toBeVisible();
    expect(screen.getByRole('button', { name: 'Descargar JSON' })).toBeEnabled();
  });

  it('un JSON inválido muestra el error y no llama a la API', async () => {
    let called = false;
    server.use(
      http.put(`${RULES_API}/import`, () => {
        called = true;
        return HttpResponse.json({});
      }),
    );
    await openRules();
    const user = userEvent.setup();

    await user.upload(
      screen.getByLabelText('Importar JSON'),
      new File(['{no es json'], 'reglas.json', { type: 'application/json' }),
    );

    expect(await screen.findByText(/no es un JSON válido/)).toBeVisible();
    expect(called).toBe(false);
  });

  it('rechaza un archivo de más de 1 MB', async () => {
    await openRules();
    const user = userEvent.setup();

    await user.upload(
      screen.getByLabelText('Importar JSON'),
      new File(['x'.repeat(1024 * 1024 + 1)], 'grande.json', { type: 'application/json' }),
    );

    expect(await screen.findByText('El archivo supera 1 MB.')).toBeVisible();
  });

  it(
    'un JSON válido se importa tras confirmar',
    async () => {
      let body: unknown;
      server.use(
        http.put(`${RULES_API}/import`, async ({ request }) => {
          body = await request.json();
          return HttpResponse.json({ version: 5, report: { errors: [], warnings: [] } });
        }),
      );
      await openRules();
      const user = userEvent.setup();

      await user.upload(
        screen.getByLabelText('Importar JSON'),
        new File(['{"encuesta":"EDIN","anio":2027}'], 'reglas.json', { type: 'application/json' }),
      );
      expect(body).toBeUndefined();
      await user.click(await screen.findByRole('button', { name: 'Importar' }));

      expect(await screen.findByText('Reglas importadas.')).toBeInTheDocument();
      expect(body).toEqual({ rules: { encuesta: 'EDIN', anio: 2027 }, version: 4 });
    },
    LONG_FLOW_TIMEOUT,
  );

  it('al cancelar la eliminación el foco vuelve al botón Eliminar de la regla', async () => {
    await openRules();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Eliminar regla c01a-no' }));
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.getByRole('button', { name: 'Eliminar regla c01a-no' })).toHaveFocus();
  });

  it('al cancelar la importación el foco vuelve al campo de archivo', async () => {
    await openRules();
    const user = userEvent.setup();

    await user.upload(
      screen.getByLabelText('Importar JSON'),
      new File(['{"encuesta":"EDIN"}'], 'reglas.json', { type: 'application/json' }),
    );
    await user.click(await screen.findByRole('button', { name: 'Cancelar' }));

    expect(screen.getByLabelText('Importar JSON')).toHaveFocus();
  });

  it('un enlace a una regla de otra página cambia de página y enfoca la fila', async () => {
    const view = rulesView();
    view.report = {
      errors: [],
      warnings: [
        { code: 'NUNCA_SE_CUMPLE', element: 'regla:anidada', questions: [], message: 'Nunca.' },
      ],
    };
    await openRules(view);
    const user = userEvent.setup();

    await user.click(screen.getByRole('link', { name: 'Ir a la regla anidada' }));

    expect(screen.getByLabelText('Página')).toHaveValue('C2');
    expect(rowOf('anidada')).toHaveFocus();
  });

  it('un enlace a una pregunta de otra página cambia de página y enfoca la pregunta', async () => {
    await openRules();
    const user = userEvent.setup();

    await user.click(screen.getByRole('link', { name: 'Ir a la pregunta C11' }));

    expect(screen.getByLabelText('Página')).toHaveValue('C2');
    expect(document.getElementById('pregunta-C11')).toHaveFocus();
  });

  it('las reglas sin ninguna columna conocida están en "Reglas sin página" y se editan', async () => {
    const view = rulesView();
    view.skips = [
      ...view.skips!,
      {
        id: 'huerfana',
        si: { pregunta: 'ZZ98', op: 'eq', valor: '1' },
        entonces: { accion: 'deshabilitar', preguntas: ['ZZ99'] },
      },
    ];
    view.report = {
      errors: [],
      warnings: [
        {
          code: 'PREGUNTA_INEXISTENTE',
          element: 'regla:huerfana',
          questions: [],
          message: 'No existe.',
        },
      ],
    };
    await openRules(view);
    const user = userEvent.setup();
    expect(document.getElementById('regla-huerfana')).toBeNull();

    await user.click(screen.getByRole('link', { name: 'Ir a la regla huerfana' }));

    expect(screen.getByRole('option', { name: 'Reglas sin página' })).toBeInTheDocument();
    expect(screen.getByLabelText('Página')).toHaveValue('__sin-pagina__');
    const row = rowOf('huerfana');
    expect(row).toHaveFocus();
    expect(within(row).getByRole('button', { name: 'Editar regla huerfana' })).toBeEnabled();
    expect(within(row).getByRole('button', { name: 'Eliminar regla huerfana' })).toBeEnabled();
  });

  it('dos guardados de validación seguidos no dan 409', async () => {
    // Servidor con estado: cada guardado sube la versión y rechaza la versión vieja.
    let current = 4;
    const sent: number[] = [];
    const statuses: number[] = [];
    server.use(
      http.get(RULES_API, () => HttpResponse.json(rulesView({ version: current }))),
      http.put(`${RULES_API}/validations/:column`, async ({ request }) => {
        const body = (await request.json()) as { version: number };
        sent.push(body.version);
        if (body.version !== current) {
          statuses.push(409);
          return HttpResponse.json(
            { title: 'Conflicto', detail: 'Otro usuario modificó el cuestionario.' },
            { status: 409, headers: PROBLEM },
          );
        }
        current += 1;
        statuses.push(200);
        return HttpResponse.json({ version: current, report: { errors: [], warnings: [] } });
      }),
    );
    renderApp(RULES_URL);
    await screen.findByText('Si C01A = No → no se muestra C01B');
    const user = userEvent.setup();

    const first = screen.getByRole('group', { name: 'Validación de C01A' });
    const second = screen.getByRole('group', { name: 'Validación de C01B' });
    await user.click(within(first).getByLabelText('Obligatoria'));
    await user.click(within(second).getByLabelText('Obligatoria'));
    await user.click(within(first).getByRole('button', { name: 'Guardar validación de C01A' }));
    await user.click(within(second).getByRole('button', { name: 'Guardar validación de C01B' }));

    await waitFor(() => expect(statuses).toHaveLength(2));
    expect(statuses).toEqual([200, 200]);
    expect(sent).toEqual([4, 5]);
  });

  it('tras guardar o importar marca también el simulador para recargarlo', async () => {
    server.use(
      http.put(`${RULES_API}/validations/:column`, () =>
        HttpResponse.json({ version: 5, report: { errors: [], warnings: [] } }),
      ),
    );
    serveRules();
    const { queryClient } = renderApp(RULES_URL);
    await screen.findByText('Si C01A = No → no se muestra C01B');
    queryClient.setQueryData(simulatorQueryKey('EDIN', 2027), { version: 4 });
    const user = userEvent.setup();

    const row = screen.getByRole('group', { name: 'Validación de C01A' });
    await user.click(within(row).getByLabelText('Obligatoria'));
    await user.click(within(row).getByRole('button', { name: 'Guardar validación de C01A' }));

    expect(await screen.findByText('Validación guardada.')).toBeInTheDocument();
    expect(queryClient.getQueryState(simulatorQueryKey('EDIN', 2027))?.isInvalidated).toBe(true);
  });
});

describe('RulesPage en un ambiente de solo lectura', () => {
  it('deshabilita toda edición, la enlaza al aviso y deja descargar', async () => {
    readOnlyEnvironment();
    await openRules();

    expect(await screen.findByRole('note')).toHaveTextContent(READ_ONLY_NOTE);
    for (const name of [
      'Nueva regla',
      'Editar regla c01a-no',
      'Eliminar regla c01a-no',
      'Guardar validación de C01A',
    ]) {
      const button = screen.getByRole('button', { name });
      expect(button).toBeDisabled();
      expect(button).toHaveAccessibleDescription(READ_ONLY_NOTE);
    }
    expect(screen.getByLabelText('Importar JSON')).toBeDisabled();
    expect(screen.getByLabelText('Importar JSON')).toHaveAccessibleDescription(READ_ONLY_NOTE);
    expect(screen.getByRole('button', { name: 'Descargar JSON' })).toBeEnabled();
  });

  it('en un año publicado no pide motivo de corrección', async () => {
    readOnlyEnvironment();
    await openRules(rulesView({ state: 'publicado' }));

    expect(await screen.findByRole('note')).toHaveTextContent(READ_ONLY_NOTE);
    expect(
      screen.queryByRole('textbox', { name: 'Motivo de la corrección' }),
    ).not.toBeInTheDocument();
  });
});
