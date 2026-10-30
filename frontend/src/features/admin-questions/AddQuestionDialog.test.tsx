import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw-server';
import {
  API_BASE,
  entry,
  PAGE_C1,
  problem,
  serveTree,
  TREE_URL,
} from '../../test/questionnaire-fixtures';
import { renderApp } from '../../test/render-app';

const NEW_ENTRY = entry({
  capProyId: 150,
  numeral: 'C30A',
  columnCode: 'C30A',
  optionGroup: 7,
  text: 'Pregunta nueva',
  rawText: 'Pregunta nueva',
  columnStatus: 'PENDING_COLUMN',
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AddQuestionDialog', () => {
  it('addQuestionShowsPendingAndScriptLink', async () => {
    let body: unknown;
    let scriptQuery: string | null = 'sin-llamar';
    serveTree();
    server.use(
      http.post(`${API_BASE}/pages/C1/entries`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          {
            version: 5,
            page: { ...PAGE_C1, entries: [...(PAGE_C1.entries ?? []), NEW_ENTRY] },
            skipsTargetingEarlierQuestions: [],
          },
          { status: 201 },
        );
      }),
      http.get(`${API_BASE}/entries/150/column-script`, ({ request }) => {
        scriptQuery = new URL(request.url).search;
        return HttpResponse.text('ALTER TABLE EDIN_27_CAP_C ADD (C30A VARCHAR2(2));');
      }),
    );
    const createUrl = vi.fn(() => 'blob:script');
    Object.defineProperty(URL, 'createObjectURL', { value: createUrl, configurable: true });
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    renderApp(TREE_URL);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Agregar pregunta en C1' }));
    const dialog = screen.getByRole('dialog', { name: 'Agregar pregunta en C1' });
    const groups = within(dialog).getByLabelText('Grupo de opciones');
    expect(
      within(groups)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual(['Sin grupo', 'Grupo 1 (como C05)', 'Grupo 7 (como H01)']);
    await user.click(within(dialog).getByLabelText('Numeral'));
    await user.paste('C30A');
    await user.click(within(dialog).getByLabelText('Código de columna'));
    await user.paste('C30A');
    await user.click(within(dialog).getByLabelText('Texto'));
    await user.paste('Pregunta nueva');
    await user.selectOptions(groups, '7');
    await user.selectOptions(within(dialog).getByLabelText('Ubicación'), '101');
    await user.click(within(dialog).getByRole('button', { name: 'Agregar' }));

    const status = await screen.findByText(
      'Se agregó C30A. Su columna está pendiente: descargue el script y entréguelo a la persona técnica.',
    );
    expect(status).toBeInTheDocument();
    expect(body).toEqual({
      numeral: 'C30A',
      columnCode: 'C30A',
      text: 'Pregunta nueva',
      inputType: 'SINGLE_CHOICE',
      optionGroup: 7,
      afterCapProyId: 101,
      version: 4,
    });
    expect(screen.getByRole('listitem', { name: 'Pregunta C30A' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Descargar script de columna' }));
    await vi.waitFor(() => expect(click).toHaveBeenCalled());
    expect(createUrl).toHaveBeenCalled();
    expect(scriptQuery).toBe('');
  });

  it('validatesRequiredFieldsAndShowsServerErrors', async () => {
    serveTree();
    server.use(
      http.post(`${API_BASE}/pages/C1/entries`, () =>
        problem(422, 'Ese numeral ya existe en la página.', {
          numeral: 'Ese numeral ya existe en la página.',
        }),
      ),
    );
    renderApp(TREE_URL);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Agregar pregunta en C1' }));
    const dialog = screen.getByRole('dialog', { name: 'Agregar pregunta en C1' });
    await user.click(within(dialog).getByRole('button', { name: 'Agregar' }));

    expect(within(dialog).getByText('Escriba el numeral.')).toBeInTheDocument();
    expect(within(dialog).getByText('Escriba el código de la columna.')).toBeInTheDocument();
    expect(within(dialog).getByText('Escriba el texto.')).toBeInTheDocument();
    expect(within(dialog).getByText('Elija el grupo de opciones.')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Numeral')).toHaveFocus();

    await user.click(within(dialog).getByLabelText('Numeral'));
    await user.paste('C05');
    await user.click(within(dialog).getByLabelText('Código de columna'));
    await user.paste('C05');
    await user.click(within(dialog).getByLabelText('Texto'));
    await user.paste('Repetida');
    await user.selectOptions(within(dialog).getByLabelText('Tipo de respuesta'), 'TEXT');
    expect(within(dialog).getByLabelText('Grupo de opciones')).toBeDisabled();
    await user.selectOptions(within(dialog).getByLabelText('Tipo de columna'), 'NUMBER');
    await user.click(within(dialog).getByRole('button', { name: 'Agregar' }));
    expect(within(dialog).getByText('Indique la longitud de la columna.')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Longitud de columna'), '3');
    await user.click(within(dialog).getByRole('button', { name: 'Agregar' }));

    expect(await within(dialog).findByText('Ese numeral ya existe en la página.')).toBeVisible();
    expect(within(dialog).getByLabelText('Numeral')).toHaveAttribute('aria-invalid', 'true');
  });
});
