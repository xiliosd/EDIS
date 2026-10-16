import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';

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
      within(summary).getByRole('link', { name: /C01A: Responda esta pregunta\./ }),
    ).toHaveAttribute('href', '#campo-C01A');
    expect(
      within(summary).getByRole('link', {
        name: /C01T: El valor está fuera del rango permitido\./,
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
      await screen.findByRole('link', { name: /C01T: La respuesta es demasiado larga\./ }),
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
});
