import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/msw-server';
import {
  API_BASE,
  LONG_FLOW_TIMEOUT,
  draftView,
  PAGE_C1,
  PAGE_H1,
  problem,
  serveTree,
  TREE_URL,
} from '../../test/questionnaire-fixtures';
import { renderApp } from '../../test/render-app';

function pageWith(changes: Partial<NonNullable<typeof PAGE_C1.entries>[number]>) {
  const [first, ...rest] = PAGE_C1.entries ?? [];
  return { ...PAGE_C1, entries: [{ ...first, ...changes }, ...rest] };
}

describe('QuestionEditor', () => {
  it(
    'editsTextWithVersionAndHandlesConflict',
    async () => {
      const bodies: unknown[] = [];
      serveTree();
      server.use(
        http.put(`${API_BASE}/entries/101/text`, async ({ request }) => {
          const body = (await request.json()) as { text: string; version: number };
          bodies.push(body);
          if (bodies.length === 1) {
            return HttpResponse.json({
              version: 5,
              page: pageWith({ text: body.text.replace('200X', '2027'), rawText: body.text }),
              skipsTargetingEarlierQuestions: [],
            });
          }
          return problem(409, 'Otro usuario modificó el cuestionario. Recargue la página.');
        }),
      );
      renderApp(TREE_URL);
      const user = userEvent.setup();

      await user.click(await screen.findByRole('button', { name: 'Editar texto de C05' }));
      const field = screen.getByLabelText('Texto de C05');
      expect(field).toHaveValue('¿En 200X trabajó en la entidad?');
      expect(field).toHaveFocus();
      await user.clear(field);
      await user.paste('¿En 200X laboró?');
      await user.click(screen.getByRole('button', { name: 'Guardar' }));

      expect(await screen.findByText('¿En 2027 laboró?')).toBeInTheDocument();
      expect(bodies[0]).toEqual({ text: '¿En 200X laboró?', version: 4 });
      expect(screen.getByRole('button', { name: 'Editar texto de C05' })).toHaveFocus();

      // La segunda edición lleva la versión que devolvió la primera; el servidor responde 409.
      await user.click(screen.getByRole('button', { name: 'Editar texto de C05' }));
      await user.type(screen.getByLabelText('Texto de C05'), '!');
      await user.click(screen.getByRole('button', { name: 'Guardar' }));

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent('Otro usuario modificó el cuestionario. Recargue la página.');
      expect(bodies[1]).toEqual({ text: '¿En 200X laboró?!', version: 5 });

      server.use(http.get(API_BASE, () => HttpResponse.json(draftView({ version: 9 }))));
      await user.click(within(alert).getByRole('button', { name: 'Recargar' }));
      expect(await screen.findByText('Versión 9')).toBeInTheDocument();
      expect(screen.queryByRole('alert')).toBeNull();
    },
    LONG_FLOW_TIMEOUT,
  );

  it('showsFieldErrorAndCancelKeepsText', async () => {
    serveTree();
    server.use(
      http.put(`${API_BASE}/entries/101/text`, () =>
        problem(422, 'El texto es demasiado largo.', { text: 'El texto es demasiado largo.' }),
      ),
    );
    renderApp(TREE_URL);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Editar texto de C05' }));
    await user.type(screen.getByLabelText('Texto de C05'), ' más');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('El texto es demasiado largo.')).toBeInTheDocument();
    expect(screen.getByLabelText('Texto de C05')).toHaveAttribute('aria-invalid', 'true');
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.getByText('¿En 2027 trabajó en la entidad?')).toBeInTheDocument();
    expect(screen.queryByLabelText('Texto de C05')).toBeNull();
  });

  it('editsHeaderAndHelp', async () => {
    const calls: Array<{ url: string; body: unknown }> = [];
    serveTree();
    server.use(
      http.put(`${API_BASE}/headers/501`, async ({ request }) => {
        const body = (await request.json()) as { text: string };
        calls.push({ url: 'header', body });
        return HttpResponse.json({
          version: 5,
          page: pageWith({ headers: [{ codEnc: 501, kind: 'HEADING', text: body.text }] }),
          skipsTargetingEarlierQuestions: [],
        });
      }),
      http.put(`${API_BASE}/help/701`, async ({ request }) => {
        const body = (await request.json()) as { text: string };
        calls.push({ url: 'help', body });
        return HttpResponse.json({
          version: 6,
          page: pageWith({
            headers: [{ codEnc: 501, kind: 'HEADING', text: 'Clima' }],
            help: { codAyu: 701, text: body.text },
          }),
          skipsTargetingEarlierQuestions: [],
        });
      }),
    );
    renderApp(TREE_URL);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Editar encabezado de C05' }));
    await user.clear(screen.getByLabelText('Encabezado de C05'));
    await user.type(screen.getByLabelText('Encabezado de C05'), 'Clima');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(await screen.findByText('Clima')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Editar ayuda de C05' }));
    await user.clear(screen.getByLabelText('Ayuda de C05'));
    await user.type(screen.getByLabelText('Ayuda de C05'), 'Nueva ayuda');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(await screen.findByText('Nueva ayuda')).toBeInTheDocument();

    expect(calls).toEqual([
      { url: 'header', body: { text: 'Clima', version: 4 } },
      { url: 'help', body: { text: 'Nueva ayuda', version: 5 } },
    ]);
  });

  it('deactivateShowsReferencingRules', async () => {
    serveTree();
    server.use(
      http.delete(`${API_BASE}/entries/101`, () =>
        problem(409, 'La pregunta la usan reglas de salto.', {
          rules: ['salto-c05', 'salto-c09'],
        }),
      ),
    );
    renderApp(TREE_URL);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Desactivar C05' }));
    const dialog = screen.getByRole('dialog', { name: 'Desactivar C05' });
    await user.click(within(dialog).getByRole('button', { name: 'Desactivar' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('La pregunta la usan reglas de salto.');
    expect(within(alert).getByText('salto-c05')).toBeInTheDocument();
    expect(within(alert).getByText('salto-c09')).toBeInTheDocument();
    expect(within(alert).queryByRole('button', { name: 'Recargar' })).toBeNull();
  });

  it('deactivatesAfterConfirmation', async () => {
    let version: string | null = null;
    serveTree();
    server.use(
      http.delete(`${API_BASE}/entries/102`, ({ request }) => {
        version = new URL(request.url).searchParams.get('version');
        return HttpResponse.json({
          version: 5,
          page: { ...PAGE_C1, entries: (PAGE_C1.entries ?? []).filter((e) => e.capProyId !== 102) },
          skipsTargetingEarlierQuestions: [],
        });
      }),
    );
    renderApp(TREE_URL);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Desactivar C06' }));
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.getByRole('listitem', { name: 'Pregunta C06' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Desactivar C06' }));
    await user.click(
      within(screen.getByRole('dialog', { name: 'Desactivar C06' })).getByRole('button', {
        name: 'Desactivar',
      }),
    );

    expect(await screen.findByText('Versión 5')).toBeInTheDocument();
    expect(screen.queryByRole('listitem', { name: 'Pregunta C06' })).toBeNull();
    expect(version).toBe('4');
    // El foco pasa a la pregunta siguiente y se anuncia el cambio (WCAG 2.4.3 y 4.1.3).
    expect(screen.getByRole('heading', { name: 'C07A' })).toHaveFocus();
    expect(screen.getByText('Se desactivó C06.')).toBeInTheDocument();
  });

  it('deactivatingTheOnlyQuestionFocusesThePageHeading', async () => {
    serveTree();
    server.use(
      http.delete(`${API_BASE}/entries/201`, () =>
        HttpResponse.json({
          version: 5,
          page: { ...PAGE_H1, entries: [] },
          skipsTargetingEarlierQuestions: [],
        }),
      ),
    );
    renderApp(TREE_URL);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Desactivar H01' }));
    await user.click(
      within(screen.getByRole('dialog', { name: 'Desactivar H01' })).getByRole('button', {
        name: 'Desactivar',
      }),
    );

    expect(await screen.findByText('Se desactivó H01.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Página H1' })).toHaveFocus();
  });

  it('textWithoutRawTextIsNotEditable', async () => {
    serveTree(draftView({ pages: [pageWith({ rawText: undefined })] }));
    renderApp(TREE_URL);

    expect(
      await screen.findByText(
        'Este texto no se puede editar porque no llegó el texto guardado. Recargue la página.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar texto de C05' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Editar ayuda de C05' })).toBeInTheDocument();
  });
});
