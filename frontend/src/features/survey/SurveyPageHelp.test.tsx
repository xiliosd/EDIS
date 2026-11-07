import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';

const YES_NO = [
  { value: '1', label: 'Sí' },
  { value: '2', label: 'No' },
];

const MANY_OPTIONS = Array.from({ length: 8 }, (_, index) => ({
  value: String(index + 1),
  label: `Opción ${index + 1}`,
}));

function help(term: string, title = `Título de ${term}`, definition = `Definición de ${term}.`) {
  return { term, title, definition };
}

function item(
  column: string,
  text: string,
  helps: ReturnType<typeof help>[],
  inputType = 'SINGLE_CHOICE',
  options = YES_NO,
) {
  return {
    id: column.length + column.charCodeAt(3),
    pageCode: 'C1',
    kind: 'ITEM',
    numeral: `${column}.`,
    label: column,
    text,
    headers: [],
    help: helps,
    answer: { inputType, columnCode: column, columnStatus: 'ACTIVE', options },
    legacySkip: null,
  };
}

// Página ficticia con ayudas en todos los tipos de texto que ve el encuestado.
const PAGE = {
  pageCode: 'C1',
  chapterCode: 'C',
  chapterTitle: 'Experiencia personal de trabajo',
  module: 'Ambiente institucional',
  state: 'AVAILABLE',
  entries: [
    {
      id: 1,
      pageCode: 'C1',
      kind: 'STATEMENT',
      numeral: 'C1.',
      label: 'C1.',
      text: 'Enunciado sobre el desempeño',
      headers: [
        {
          kind: 'HEADING',
          text: 'Ubicación laboral',
          help: [help('laboral', 'Lugar de trabajo', 'Primera línea\nSegunda línea')],
        },
      ],
      help: [help('desempeño')],
      answer: null,
      legacySkip: null,
    },
    item('C01A', '¿Ficticia C01A sobre el cargo?', [help('cargo'), help('vínculo')]),
    item('C01B', 'Lista ficticia C01B del nivel', [help('nivel')], 'SINGLE_CHOICE', MANY_OPTIONS),
    item('C01C', 'Casilla ficticia de teletrabajo', [help('teletrabajo')], 'CHECKBOX', []),
    item('C01T', 'Años en la entidad', [help('entidad'), help('entre')], 'TEXT', []),
  ],
  answers: {},
};

const RULES = {
  encuesta: 'EDIN',
  anio: 2026,
  version: 1,
  saltos: [],
  validaciones: { C01T: { tipo: 'entero', min: 0, max: 60 } },
};

beforeEach(() => {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({
        accountType: 'RESPONDENT',
        roles: ['ENCUESTADO'],
        surveyCode: 'EDIN',
        surveyYear: 2026,
      }),
    ),
    http.get('*/api/v1/survey/rules', () => HttpResponse.json(RULES)),
    http.get('*/api/v1/survey/pages/C1', () => HttpResponse.json(PAGE)),
  );
});

async function openPage() {
  renderApp('/encuesta/pagina/C1');
  return screen.findByRole('radiogroup', { name: /C01A/ });
}

function panelOf(button: HTMLElement): HTMLElement {
  const panel = document.getElementById(button.getAttribute('aria-controls') ?? '');
  expect(panel).not.toBeNull();
  return panel as HTMLElement;
}

describe('Ayudas del encuestado', () => {
  it('muestra el término como botón y abre y cierra la definición debajo del texto', async () => {
    const group = await openPage();
    const term = within(group).getByRole('button', { name: 'cargo' });
    const panel = panelOf(term);
    expect(term).toHaveAttribute('aria-expanded', 'false');
    expect(panel).not.toBeVisible();

    await userEvent.click(term);

    expect(term).toHaveAttribute('aria-expanded', 'true');
    expect(panel).toBeVisible();
    expect(within(panel).getByText('Título de cargo')).toBeVisible();
    expect(within(panel).getByText('Definición de cargo.')).toBeVisible();

    await userEvent.click(within(panel).getByRole('button', { name: 'Cerrar ayuda: cargo' }));

    expect(panel).not.toBeVisible();
    expect(term).toHaveAttribute('aria-expanded', 'false');
    expect(term).toHaveFocus();
  });

  it('Escape cierra la ayuda abierta y devuelve el foco al término', async () => {
    const group = await openPage();
    const term = within(group).getByRole('button', { name: 'cargo' });
    await userEvent.click(term);
    within(panelOf(term)).getByRole('button', { name: 'Cerrar ayuda: cargo' }).focus();

    await userEvent.keyboard('{Escape}');

    expect(panelOf(term)).not.toBeVisible();
    expect(term).toHaveFocus();
  });

  it('agrega «Ayuda: …» cuando el término no está en el texto, y cada ayuda abre sola', async () => {
    await openPage();
    const extra = screen.getByRole('button', { name: 'Ayuda: vínculo' });
    const term = screen.getByRole('button', { name: 'cargo' });

    await userEvent.click(extra);
    await userEvent.click(term);

    expect(panelOf(extra)).toBeVisible();
    expect(panelOf(term)).toBeVisible();
    expect(within(panelOf(extra)).getByText('Título de vínculo')).toBeVisible();
  });

  it('mantiene el texto completo como nombre accesible de cada campo', async () => {
    await openPage();
    expect(
      screen.getByRole('radiogroup', { name: 'C01A ¿Ficticia C01A sobre el cargo?' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', { name: 'C01B Lista ficticia C01B del nivel' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', { name: 'C01C Casilla ficticia de teletrabajo' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', { name: 'C01T Años en la entidad (entre 0 y 60)' }),
    ).toBeInTheDocument();
  });

  it('el clic en el término no marca ni enfoca la respuesta', async () => {
    await openPage();
    const checkbox = screen.getByRole('checkbox', { name: /teletrabajo/ });
    const textbox = screen.getByRole('textbox', { name: /Años en la entidad/ });

    await userEvent.click(screen.getByRole('button', { name: 'teletrabajo' }));
    await userEvent.click(screen.getByRole('button', { name: 'entidad' }));

    expect(checkbox).not.toBeChecked();
    expect(textbox).not.toHaveFocus();
    expect(screen.getByRole('button', { name: 'entidad' })).toHaveFocus();
    expect(screen.getByRole('button', { name: 'teletrabajo' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
  });

  it('encabezados y enunciados también enlazan su término y respetan los saltos de línea', async () => {
    await openPage();
    const heading = screen.getByRole('heading', { level: 2, name: 'Ubicación laboral' });
    const term = within(heading).getByRole('button', { name: 'laboral' });

    await userEvent.click(term);

    const panel = panelOf(term);
    expect(within(panel).getByText('Lugar de trabajo')).toBeVisible();
    expect(within(panel).getByText(/Primera línea/).textContent).toBe(
      'Primera línea\nSegunda línea',
    );
    expect(screen.getByText(/Enunciado sobre el/)).toContainElement(
      screen.getByRole('button', { name: 'desempeño' }),
    );
  });

  it('Escape también cierra desde cualquier punto dentro del panel', async () => {
    const group = await openPage();
    const term = within(group).getByRole('button', { name: 'cargo' });
    await userEvent.click(term);

    fireEvent.keyDown(within(panelOf(term)).getByText('Definición de cargo.'), { key: 'Escape' });

    expect(panelOf(term)).not.toBeVisible();
    expect(term).toHaveFocus();
  });

  it('cada «Cerrar ayuda» nombra su término cuando hay varias abiertas', async () => {
    await openPage();
    await userEvent.click(screen.getByRole('button', { name: 'cargo' }));
    await userEvent.click(screen.getByRole('button', { name: 'Ayuda: vínculo' }));

    expect(screen.getByRole('button', { name: 'Cerrar ayuda: cargo' })).toHaveTextContent(
      'Cerrar ayuda',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar ayuda: vínculo' }));

    expect(screen.getByRole('button', { name: 'Ayuda: vínculo' })).toHaveFocus();
    expect(screen.getByRole('button', { name: 'Cerrar ayuda: cargo' })).toBeVisible();
  });

  it('no enlaza un término dentro del rango de una pregunta numérica', async () => {
    await openPage();
    const label = screen.getByText(/Años en la/, { selector: 'label' });

    expect(label).toHaveTextContent('C01T Años en la entidad (entre 0 y 60)');
    expect(within(label).queryByRole('button', { name: 'entre' })).not.toBeInTheDocument();
    expect(within(label).getByRole('button', { name: 'entidad' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ayuda: entre' })).toBeInTheDocument();
  });

  it('muestra como texto literal el marcado que traiga una ayuda', async () => {
    const script = '<script>alert(1)</script>';
    server.use(
      http.get('*/api/v1/survey/pages/C1', () =>
        HttpResponse.json({
          ...PAGE,
          entries: [
            item('C01A', `¿Ficticia ${script}?`, [help(script, `Título ${script}`, script)]),
          ],
        }),
      ),
    );
    await openPage();
    const term = screen.getByRole('button', { name: script });

    await userEvent.click(term);

    expect(within(panelOf(term)).getByText(`Título ${script}`)).toBeVisible();
    expect(within(panelOf(term)).getByText(script)).toBeVisible();
    expect(document.querySelector('script')).toBeNull();
  });

  it('no tiene violaciones de axe con las ayudas abiertas y cerradas', async () => {
    await openPage();
    const container = document.body;
    // jsdom no calcula estilos: el contraste se revisa en E2E y con contrast.test.ts.
    const options = { rules: { 'color-contrast': { enabled: false } } };
    expect((await axe.run(container, options)).violations).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: 'cargo' }));
    await userEvent.click(screen.getByRole('button', { name: 'Ayuda: vínculo' }));

    expect((await axe.run(container, options)).violations).toEqual([]);
  });
});
