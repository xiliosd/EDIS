import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';
import {
  bindDraftsToUser,
  DRAFT_OWNER_KEY,
  draftOwnerFor,
  SESSION_GENERATION_KEY,
} from './page-drafts';

const YES_NO = [
  { value: '1', label: 'Sí' },
  { value: '2', label: 'No' },
];

function item(column: string, text: string, inputType = 'SINGLE_CHOICE', options = YES_NO) {
  return {
    id: column.length,
    pageCode: 'C1',
    kind: 'ITEM',
    numeral: `${column}.`,
    label: column,
    text,
    headers: [],
    answer: { inputType, columnCode: column, columnStatus: 'ACTIVE', options },
    legacySkip: null,
  };
}

function page(state = 'AVAILABLE', answers: Record<string, string> = {}) {
  return {
    pageCode: 'C1',
    chapterCode: 'C',
    chapterTitle: 'Experiencia personal de trabajo',
    module: 'Ambiente institucional',
    state,
    entries: [
      item('C01A', '¿Pregunta ficticia C01A?'),
      item('C01B', 'Pregunta ficticia C01B'),
      item('C01T', 'Años en la entidad', 'TEXT', []),
    ],
    answers,
  };
}

const RULES = {
  encuesta: 'EDIN',
  anio: 2026,
  version: 1,
  saltos: [
    {
      id: 'legacy-C01A',
      si: { pregunta: 'C01A', op: 'eq', valor: '2' },
      entonces: { accion: 'deshabilitar', preguntas: ['C01B', 'D01A'] },
    },
  ],
  validaciones: { C01T: { tipo: 'entero', min: 0, max: 60 } },
};

const NEXT_PAGE_HEADING = 'C. Página siguiente ficticia';

let savedBody: unknown;

function nextPageHandler() {
  return http.get('*/api/v1/survey/pages/C2', () =>
    HttpResponse.json({
      ...page(),
      pageCode: 'C2',
      chapterTitle: 'Página siguiente ficticia',
      entries: [],
    }),
  );
}

function saveFails(status: number, detail: string) {
  return http.put('*/api/v1/survey/pages/C1/answers', () =>
    HttpResponse.json({ title: 'Error', status, detail }, { status }),
  );
}

/** Responde la página C1 de forma válida, pide guardar y confirma. */
async function answerAndConfirm() {
  const group = await screen.findByRole('radiogroup', { name: /C01A/ });
  await userEvent.click(within(group).getByRole('radio', { name: 'Sí' }));
  await userEvent.click(
    within(screen.getByRole('radiogroup', { name: /C01B/ })).getByRole('radio', { name: 'Sí' }),
  );
  await userEvent.type(screen.getByLabelText(/Años en la entidad/), '12');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar página' }));
  await userEvent.click(screen.getByRole('button', { name: 'Guardar y continuar' }));
}

/** Responde C1, el guardado responde 401 y queda el borrador de la página. */
async function stashDraftAfterExpiredSession() {
  server.use(saveFails(401, 'No autenticado.'));
  const view = renderApp('/encuesta/pagina/C1');
  await answerAndConfirm();
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Su sesión venció. Vuelva a ingresar; las respuestas de esta página no se guardaron.',
  );
  return view;
}

async function expectDraftRestored() {
  const view = renderApp('/encuesta/pagina/C1');
  const group = await screen.findByRole('radiogroup', { name: /C01A/ });
  await vi.waitFor(() => expect(within(group).getByRole('radio', { name: 'Sí' })).toBeChecked());
  expect(screen.getByLabelText(/Años en la entidad/)).toHaveValue('12');
  return view;
}

/** Ingresa por la pantalla de ingreso con un usuario ficticio. */
async function loginAs(username: string) {
  server.use(
    http.post('*/api/v1/auth/login', () =>
      HttpResponse.json({
        accountType: 'RESPONDENT',
        roles: ['ENCUESTADO'],
        surveyCode: 'EDIN',
        surveyYear: 2026,
      }),
    ),
  );
  const view = renderApp('/ingresar');
  await userEvent.type(await screen.findByLabelText('Usuario'), username);
  await userEvent.type(screen.getByLabelText('Clave'), 'Clave#ficticia');
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
  expect(await screen.findByRole('heading', { name: 'Encuesta EDIN 2026' })).toBeInTheDocument();
  view.unmount();
}

beforeEach(() => {
  savedBody = undefined;
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({
        accountType: 'RESPONDENT',
        roles: ['ENCUESTADO'],
        surveyCode: 'EDIN',
        surveyYear: 2026,
      }),
    ),
    http.get('*/api/v1/auth/csrf', () => {
      document.cookie = 'XSRF-TOKEN=prueba; path=/';
      return HttpResponse.json({ headerName: 'X-XSRF-TOKEN' });
    }),
    http.get('*/api/v1/survey/rules', () => HttpResponse.json(RULES)),
    http.get('*/api/v1/survey/pages/C1', () => HttpResponse.json(page())),
    http.get('*/api/v1/survey/progress', () =>
      HttpResponse.json({ pages: [], finished: false, observationsSaved: false, receipt: null }),
    ),
  );
});

describe('SurveyPageView', () => {
  it('muestra la página con radios agrupados y el rango en la etiqueta', async () => {
    renderApp('/encuesta/pagina/C1');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'C. Experiencia personal de trabajo' }),
    ).toBeInTheDocument();
    const group = screen.getByRole('radiogroup', { name: /Pregunta ficticia C01A/ });
    expect(within(group).getAllByRole('radio')).toHaveLength(2);
    expect(screen.getByLabelText(/Años en la entidad \(entre 0 y 60\)/)).toHaveAttribute(
      'inputmode',
      'numeric',
    );
    expect(document.title).toBe('C. Experiencia personal de trabajo · Encuesta EDI');
  });

  it('aplica el salto en vivo y anuncia cuántas preguntas no aplican', async () => {
    renderApp('/encuesta/pagina/C1');
    const group = await screen.findByRole('radiogroup', { name: /C01A/ });

    await userEvent.click(within(group).getByRole('radio', { name: 'No' }));

    expect(screen.queryByRole('radiogroup', { name: /C01B/ })).not.toBeInTheDocument();
    expect(screen.getByText('No aplica según sus respuestas anteriores.')).toBeInTheDocument();
    expect(screen.getByText('1 pregunta no aplica según sus respuestas.')).toBeInTheDocument();
  });

  it('muestra los errores locales con un resumen enfocado y enlaces a cada campo', async () => {
    renderApp('/encuesta/pagina/C1');
    await screen.findByRole('radiogroup', { name: /C01A/ });
    await userEvent.type(screen.getByLabelText(/Años en la entidad/), '99');

    await userEvent.click(screen.getByRole('button', { name: 'Guardar página' }));

    const summary = screen.getByRole('alert');
    expect(summary).toHaveFocus();
    expect(
      within(summary).getByRole('link', {
        name: 'C01A ¿Pregunta ficticia C01A?: Responda esta pregunta.',
      }),
    ).toHaveAttribute('href', '#campo-C01A');
    expect(
      within(summary).getByRole('link', {
        name: 'C01T Años en la entidad: El valor está fuera del rango permitido.',
      }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Años en la entidad/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('confirma antes de guardar, envía las deshabilitadas vacías y pasa a la siguiente', async () => {
    server.use(
      http.put('*/api/v1/survey/pages/C1/answers', async ({ request }) => {
        savedBody = await request.json();
        return HttpResponse.json({ pageCode: 'C1', nextPageCode: 'C2' });
      }),
      nextPageHandler(),
    );
    renderApp('/encuesta/pagina/C1');
    const group = await screen.findByRole('radiogroup', { name: /C01A/ });
    await userEvent.click(within(group).getByRole('radio', { name: 'No' }));
    await userEvent.type(screen.getByLabelText(/Años en la entidad/), '12');

    await userEvent.click(screen.getByRole('button', { name: 'Guardar página' }));
    const dialog = screen.getByRole('dialog', { name: '¿Guardar esta página?' });
    expect(dialog).toHaveTextContent(
      'Después de guardar no podrá cambiar las respuestas de esta página.',
    );
    await userEvent.click(within(dialog).getByRole('button', { name: 'Guardar y continuar' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: NEXT_PAGE_HEADING }),
    ).toBeInTheDocument();
    await vi.waitFor(() =>
      expect(savedBody).toEqual({ answers: { C01A: '2', C01B: '', C01T: '12' } }),
    );
  });

  it('pinta los errores que devuelve el backend', async () => {
    server.use(
      http.put('*/api/v1/survey/pages/C1/answers', () =>
        HttpResponse.json(
          {
            title: 'Respuestas inválidas',
            status: 422,
            detail: 'Revise las preguntas marcadas.',
            errors: { C01T: 'La respuesta es demasiado larga.' },
          },
          { status: 422 },
        ),
      ),
    );
    renderApp('/encuesta/pagina/C1');
    const group = await screen.findByRole('radiogroup', { name: /C01A/ });
    await userEvent.click(within(group).getByRole('radio', { name: 'Sí' }));
    await userEvent.click(
      within(screen.getByRole('radiogroup', { name: /C01B/ })).getByRole('radio', { name: 'Sí' }),
    );
    await userEvent.type(screen.getByLabelText(/Años en la entidad/), '12');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar página' }));
    await userEvent.click(screen.getByRole('button', { name: 'Guardar y continuar' }));

    expect(
      await screen.findByRole('link', {
        name: 'C01T Años en la entidad: La respuesta es demasiado larga.',
      }),
    ).toBeInTheDocument();
  });

  it('muestra una página guardada en solo lectura con las etiquetas de las opciones', async () => {
    server.use(
      http.get('*/api/v1/survey/pages/C1', () =>
        HttpResponse.json(page('SAVED', { C01A: '2', C01B: '0', C01T: '12' })),
      ),
    );
    renderApp('/encuesta/pagina/C1');

    expect(await screen.findByText('Esta página ya fue guardada.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar página' })).not.toBeInTheDocument();
    const answers = screen.getByRole('list', { name: 'Respuestas guardadas' });
    expect(answers).toHaveTextContent('No');
    expect(answers).toHaveTextContent('No aplica');
    expect(answers).toHaveTextContent('12');
  });

  it('avisa cuando la página todavía está bloqueada', async () => {
    server.use(http.get('*/api/v1/survey/pages/C1', () => HttpResponse.json(page('LOCKED'))));
    renderApp('/encuesta/pagina/C1');

    expect(await screen.findByText('Complete primero las páginas anteriores.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute(
      'href',
      '/encuesta',
    );
  });

  it('usa una lista desplegable con más de 7 opciones y una casilla que vale 1 o 0', async () => {
    const manyOptions = Array.from({ length: 8 }, (_, index) => ({
      value: String(index + 1),
      label: `Opción ${index + 1}`,
    }));
    server.use(
      http.get('*/api/v1/survey/pages/C1', () =>
        HttpResponse.json({
          ...page(),
          entries: [
            item('C02S', 'Cargo ficticio', 'SINGLE_CHOICE', manyOptions),
            item('C02X', 'Marque si aplica', 'CHECKBOX', []),
          ],
        }),
      ),
      http.put('*/api/v1/survey/pages/C1/answers', async ({ request }) => {
        savedBody = await request.json();
        return HttpResponse.json({ pageCode: 'C1', nextPageCode: 'C2' });
      }),
      nextPageHandler(),
    );
    renderApp('/encuesta/pagina/C1');

    const select = await screen.findByRole('combobox', { name: /Cargo ficticio/ });
    await userEvent.selectOptions(select, 'Opción 8');
    const checkbox = screen.getByRole('checkbox', { name: /Marque si aplica/ });
    await userEvent.click(checkbox);
    expect(checkbox).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Guardar página' }));
    await userEvent.click(screen.getByRole('button', { name: 'Guardar y continuar' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: NEXT_PAGE_HEADING }),
    ).toBeInTheDocument();
    await vi.waitFor(() => expect(savedBody).toEqual({ answers: { C02S: '8', C02X: '1' } }));
  });

  it('pasa a terminar la encuesta cuando no hay página siguiente', async () => {
    server.use(
      http.put('*/api/v1/survey/pages/C1/answers', () =>
        HttpResponse.json({ pageCode: 'C1', nextPageCode: null }),
      ),
    );
    renderApp('/encuesta/pagina/C1');
    await answerAndConfirm();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Terminar la encuesta' }),
    ).toBeInTheDocument();
  });

  it('ante un 409 muestra el detalle y recarga la página ya guardada', async () => {
    let pageRequests = 0;
    server.use(
      http.get('*/api/v1/survey/pages/C1', () => {
        pageRequests += 1;
        return HttpResponse.json(
          pageRequests === 1 ? page() : page('SAVED', { C01A: '1', C01B: '1', C01T: '12' }),
        );
      }),
      saveFails(409, 'Esta página ya fue guardada.'),
    );
    renderApp('/encuesta/pagina/C1');
    await answerAndConfirm();

    expect(await screen.findByRole('list', { name: 'Respuestas guardadas' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Esta página ya fue guardada.');
    expect(screen.queryByRole('button', { name: 'Guardar página' })).not.toBeInTheDocument();
  });

  it('muestra un mensaje genérico si el guardado falla por otra causa', async () => {
    server.use(saveFails(500, 'Error interno.'));
    renderApp('/encuesta/pagina/C1');
    await answerAndConfirm();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No fue posible guardar. Intente de nuevo.',
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('si la sesión venció guarda un borrador y lo recupera al volver a la página', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    const first = await stashDraftAfterExpiredSession();

    const alert = screen.getByRole('alert');
    expect(within(alert).getByRole('link', { name: 'Ingresar de nuevo' })).toHaveAttribute(
      'href',
      '/ingresar',
    );
    const stored = JSON.parse(window.sessionStorage.getItem('edi-borrador-C1') ?? '{}') as {
      owner: string;
      values: Record<string, string>;
    };
    expect(stored.values).toEqual({ C01A: '1', C01B: '1', C01T: '12' });
    expect(stored.owner).toBe(window.sessionStorage.getItem(DRAFT_OWNER_KEY));
    first.unmount();

    await expectDraftRestored();
    expect(window.sessionStorage.getItem('edi-borrador-C1')).toBeNull();
  });

  it('el mismo usuario recupera su borrador después de ingresar de nuevo', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    (await stashDraftAfterExpiredSession()).unmount();
    const generationBefore = window.localStorage.getItem(SESSION_GENERATION_KEY);

    await loginAs(' prueba05 ');
    // El ingreso estrena generación (las demás pestañas descartan), pero esta pestaña la adopta.
    expect(window.localStorage.getItem(SESSION_GENERATION_KEY)).not.toBe(generationBefore);
    await expectDraftRestored();
  });

  it('otro usuario no recibe el borrador del anterior', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    (await stashDraftAfterExpiredSession()).unmount();

    await loginAs('PRUEBA07');
    expect(window.sessionStorage.getItem('edi-borrador-C1')).toBeNull();

    renderApp('/encuesta/pagina/C1');
    const group = await screen.findByRole('radiogroup', { name: /C01A/ });
    expect(within(group).getByRole('radio', { name: 'Sí' })).not.toBeChecked();
    expect(screen.getByLabelText(/Años en la entidad/)).toHaveValue('');
  });

  it('descarta un borrador cuyo dueño no es el usuario actual', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    window.sessionStorage.setItem(
      'edi-borrador-C1',
      JSON.stringify({ owner: 'f'.repeat(64), values: { C01T: '7' } }),
    );
    renderApp('/encuesta/pagina/C1');
    await screen.findByRole('radiogroup', { name: /C01A/ });

    expect(screen.getByLabelText(/Años en la entidad/)).toHaveValue('');
    expect(window.sessionStorage.getItem('edi-borrador-C1')).toBeNull();
  });

  it('cerrar sesión borra los borradores y la huella del usuario', async () => {
    server.use(http.post('*/api/v1/auth/logout', () => HttpResponse.json({}, { status: 503 })));
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    await stashDraftAfterExpiredSession();
    expect(window.sessionStorage.getItem('edi-borrador-C1')).not.toBeNull();
    const generationBefore = window.localStorage.getItem(SESSION_GENERATION_KEY);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);

    // El servidor falla al cerrar: aun así no queda nada para el siguiente usuario.
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    expect(
      await screen.findByRole('heading', { name: 'Ingreso a la encuesta' }),
    ).toBeInTheDocument();
    expect(window.sessionStorage.getItem('edi-borrador-C1')).toBeNull();
    expect(window.sessionStorage.getItem(DRAFT_OWNER_KEY)).toBeNull();
    // Las demás pestañas también descartan sus borradores.
    expect(window.localStorage.getItem(SESSION_GENERATION_KEY)).not.toBeNull();
    expect(window.localStorage.getItem(SESSION_GENERATION_KEY)).not.toBe(generationBefore);
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog', { name: '¿Salir sin guardar?' })).not.toBeInTheDocument();
    confirm.mockRestore();
  });

  it('descarta los borradores de la pestaña si otra pestaña cambia la generación', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    await stashDraftAfterExpiredSession();
    expect(window.sessionStorage.getItem('edi-borrador-C1')).not.toBeNull();

    act(() => {
      window.localStorage.setItem(SESSION_GENERATION_KEY, 'generacion-ficticia-2');
      window.dispatchEvent(
        new StorageEvent('storage', {
          key: SESSION_GENERATION_KEY,
          newValue: 'generacion-ficticia-2',
          storageArea: window.localStorage,
        }),
      );
    });

    expect(window.sessionStorage.getItem('edi-borrador-C1')).toBeNull();
  });

  it('la huella del usuario es un SHA-256 en hex sin el usuario en claro', async () => {
    await loginAs('PRUEBA05');

    const owner = window.sessionStorage.getItem(DRAFT_OWNER_KEY) ?? '';
    expect(owner).toMatch(/^[0-9a-f]{64}$/);
    expect(owner.toLowerCase()).not.toContain('prueba05');
    expect(await draftOwnerFor('RESPONDENT', 'PRUEBA05')).toBe(owner);
  });

  it('borra el borrador de la página después de guardarla', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    const owner = window.sessionStorage.getItem(DRAFT_OWNER_KEY);
    window.sessionStorage.setItem(
      'edi-borrador-C1',
      JSON.stringify({ owner, values: { C01T: '7', C_X: 1 } }),
    );
    server.use(
      http.put('*/api/v1/survey/pages/C1/answers', () =>
        HttpResponse.json({ pageCode: 'C1', nextPageCode: 'C2' }),
      ),
      nextPageHandler(),
    );
    renderApp('/encuesta/pagina/C1');
    await screen.findByRole('radiogroup', { name: /C01A/ });
    await vi.waitFor(() => expect(screen.getByLabelText(/Años en la entidad/)).toHaveValue('7'));
    window.sessionStorage.setItem('edi-borrador-C1', '{}');
    await userEvent.clear(screen.getByLabelText(/Años en la entidad/));
    await answerAndConfirm();

    expect(
      await screen.findByRole('heading', { level: 1, name: NEXT_PAGE_HEADING }),
    ).toBeInTheDocument();
    expect(window.sessionStorage.getItem('edi-borrador-C1')).toBeNull();
  });

  it('pide recargar si el token CSRF ya no corresponde', async () => {
    server.use(saveFails(403, 'Prohibido.'));
    renderApp('/encuesta/pagina/C1');
    await answerAndConfirm();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La sesión de seguridad cambió. Recargue la página e intente de nuevo.',
    );
    expect(screen.queryByRole('link', { name: 'Ingresar de nuevo' })).not.toBeInTheDocument();
    expect(window.sessionStorage.getItem('edi-borrador-C1')).toBeNull();
  });

  it('pregunta antes de cerrar sesión si hay respuestas sin guardar', async () => {
    let logouts = 0;
    server.use(
      http.post('*/api/v1/auth/logout', () => {
        logouts += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValue(true);
    renderApp('/encuesta/pagina/C1');
    await userEvent.type(await screen.findByLabelText(/Años en la entidad/), '12');

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    expect(confirm).toHaveBeenCalledWith('Tiene respuestas sin guardar. ¿Desea salir?');
    expect(logouts).toBe(0);
    expect(screen.getByLabelText(/Años en la entidad/)).toHaveValue('12');

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    await vi.waitFor(() => expect(logouts).toBe(1));
    confirm.mockRestore();
  });

  it('muestra el detalle de un 422 que no trae errores por campo', async () => {
    server.use(saveFails(422, 'Las respuestas no son válidas.'));
    renderApp('/encuesta/pagina/C1');
    await answerAndConfirm();

    expect(await screen.findByRole('alert')).toHaveTextContent('Las respuestas no son válidas.');
  });

  it('devuelve a cero una casilla que deja de aplicar y vuelve a aplicar', async () => {
    server.use(
      http.get('*/api/v1/survey/rules', () =>
        HttpResponse.json({
          ...RULES,
          saltos: [
            {
              id: 'legacy-C01A',
              si: { pregunta: 'C01A', op: 'eq', valor: '2' },
              entonces: { accion: 'deshabilitar', preguntas: ['C02X'] },
            },
          ],
          validaciones: {},
        }),
      ),
      http.get('*/api/v1/survey/pages/C1', () =>
        HttpResponse.json({
          ...page(),
          entries: [
            item('C01A', '¿Pregunta ficticia C01A?'),
            item('C02X', 'Marque si aplica', 'CHECKBOX', []),
          ],
        }),
      ),
      http.put('*/api/v1/survey/pages/C1/answers', async ({ request }) => {
        savedBody = await request.json();
        return HttpResponse.json({ pageCode: 'C1', nextPageCode: 'C2' });
      }),
      nextPageHandler(),
    );
    renderApp('/encuesta/pagina/C1');
    const group = await screen.findByRole('radiogroup', { name: /C01A/ });
    await userEvent.click(screen.getByRole('checkbox', { name: /Marque si aplica/ }));
    await userEvent.click(within(group).getByRole('radio', { name: 'No' }));
    await userEvent.click(within(group).getByRole('radio', { name: 'Sí' }));

    expect(screen.getByRole('checkbox', { name: /Marque si aplica/ })).not.toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Guardar página' }));
    await userEvent.click(screen.getByRole('button', { name: 'Guardar y continuar' }));
    await vi.waitFor(() => expect(savedBody).toEqual({ answers: { C01A: '1', C02X: '0' } }));
  });

  describe('al salir de la página con respuestas sin guardar', () => {
    const LEAVE_DIALOG = '¿Salir sin guardar?';

    it('"Volver al inicio" pide confirmar y "Seguir en la página" conserva las respuestas', async () => {
      renderApp('/encuesta/pagina/C1');
      await userEvent.type(await screen.findByLabelText(/Años en la entidad/), '12');

      await userEvent.click(screen.getByRole('button', { name: 'Volver al inicio' }));

      const dialog = screen.getByRole('dialog', { name: LEAVE_DIALOG });
      expect(dialog).toHaveTextContent(
        'Tiene respuestas sin guardar en esta página. Si sale, se perderán.',
      );
      await userEvent.click(within(dialog).getByRole('button', { name: 'Seguir en la página' }));
      expect(screen.queryByRole('dialog', { name: LEAVE_DIALOG })).not.toBeInTheDocument();
      expect(screen.getByLabelText(/Años en la entidad/)).toHaveValue('12');
      expect(
        screen.getByRole('heading', { level: 1, name: 'C. Experiencia personal de trabajo' }),
      ).toBeInTheDocument();
    });

    it('con el botón Atrás pide confirmar y "Salir sin guardar" navega', async () => {
      const { router } = renderApp(['/encuesta', '/encuesta/pagina/C1']);
      await userEvent.type(await screen.findByLabelText(/Años en la entidad/), '12');

      await act(() => router.navigate(-1));

      const dialog = await screen.findByRole('dialog', { name: LEAVE_DIALOG });
      expect(router.state.location.pathname).toBe('/encuesta/pagina/C1');
      await userEvent.click(within(dialog).getByRole('button', { name: 'Salir sin guardar' }));
      expect(
        await screen.findByRole('heading', { name: 'Encuesta EDIN 2026' }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe('/encuesta');
    });

    it('sin cambios navega sin preguntar', async () => {
      const { router } = renderApp(['/encuesta', '/encuesta/pagina/C1']);
      await screen.findByLabelText(/Años en la entidad/);

      await act(() => router.navigate(-1));

      expect(
        await screen.findByRole('heading', { name: 'Encuesta EDIN 2026' }),
      ).toBeInTheDocument();
      expect(screen.queryByRole('dialog', { name: LEAVE_DIALOG })).not.toBeInTheDocument();
    });

    it('tras un 401 deja ir a ingresar sin preguntar y el borrador incluye lo editado', async () => {
      await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
      await stashDraftAfterExpiredSession();
      const years = screen.getByLabelText(/Años en la entidad/);
      await userEvent.clear(years);
      await userEvent.type(years, '15');

      await userEvent.click(screen.getByRole('link', { name: 'Ingresar de nuevo' }));

      expect(
        await screen.findByRole('heading', { name: 'Ingreso a la encuesta' }),
      ).toBeInTheDocument();
      expect(screen.queryByRole('dialog', { name: LEAVE_DIALOG })).not.toBeInTheDocument();
      const stored = JSON.parse(window.sessionStorage.getItem('edi-borrador-C1') ?? '{}') as {
        values: Record<string, string>;
      };
      expect(stored.values.C01T).toBe('15');
    });

    it('no bloquea el paso a la página siguiente después de guardar', async () => {
      server.use(
        http.put('*/api/v1/survey/pages/C1/answers', () =>
          HttpResponse.json({ pageCode: 'C1', nextPageCode: 'C2' }),
        ),
        nextPageHandler(),
      );
      const { router } = renderApp('/encuesta/pagina/C1');
      await answerAndConfirm();

      expect(
        await screen.findByRole('heading', { level: 1, name: NEXT_PAGE_HEADING }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe('/encuesta/pagina/C2');
      expect(screen.queryByRole('dialog', { name: LEAVE_DIALOG })).not.toBeInTheDocument();
    });
  });
});
