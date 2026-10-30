import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/msw-server';
import { API_BASE, PAGE_C1, problem, serveTree, TREE_URL } from '../../test/questionnaire-fixtures';
import { renderApp } from '../../test/render-app';

function serveReorder(bodies: unknown[], skips: string[] = []) {
  server.use(
    http.patch(`${API_BASE}/pages/C1/order`, async ({ request }) => {
      const body = (await request.json()) as { capProyIds: number[]; version: number };
      bodies.push(body);
      const byId = new Map((PAGE_C1.entries ?? []).map((item) => [item.capProyId, item]));
      return HttpResponse.json({
        version: body.version + 1,
        page: { ...PAGE_C1, entries: body.capProyIds.map((id) => byId.get(id)) },
        skipsTargetingEarlierQuestions: skips,
      });
    }),
  );
}

function numeralsInOrder() {
  const list = screen.getByRole('list', { name: 'Preguntas de la página C1' });
  return within(list)
    .getAllByRole('listitem')
    .map((item) => item.getAttribute('aria-label'));
}

describe('ReorderList', () => {
  it('reorderByKeyboardAnnouncesPosition', async () => {
    const bodies: unknown[] = [];
    serveTree();
    serveReorder(bodies, ['El salto de C09 apunta a C07A, que ahora está antes.']);
    renderApp(TREE_URL);
    const user = userEvent.setup();

    const up = await screen.findByRole('button', { name: 'Subir C07A' });
    expect(screen.getByRole('button', { name: 'Subir C05' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Bajar C07A' })).toBeDisabled();
    up.focus();
    await user.keyboard('{Enter}');

    expect(await screen.findByText('C07A ahora es la pregunta 2 de 3')).toBeInTheDocument();
    expect(bodies[0]).toEqual({ capProyIds: [101, 103, 102], version: 4 });
    expect(numeralsInOrder()).toEqual(['Pregunta C05', 'Pregunta C07A', 'Pregunta C06']);
    expect(screen.getByRole('button', { name: 'Subir C07A' })).toHaveFocus();
    expect(
      screen.getByText('El salto de C09 apunta a C07A, que ahora está antes.'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Subir C07A' }));
    expect(await screen.findByText('C07A ahora es la pregunta 1 de 3')).toBeInTheDocument();
    expect(bodies[1]).toEqual({ capProyIds: [103, 101, 102], version: 5 });
    // En el borde, el foco pasa al otro botón porque Subir queda deshabilitado.
    expect(screen.getByRole('button', { name: 'Bajar C07A' })).toHaveFocus();
  });

  it('reordersByDragAndDrop', async () => {
    const bodies: unknown[] = [];
    serveTree();
    serveReorder(bodies);
    renderApp(TREE_URL);

    const list = await screen.findByRole('list', { name: 'Preguntas de la página C1' });
    const items = within(list).getAllByRole('listitem');
    const handle = within(items[0]).getByTitle('Arrastrar C05');
    fireEvent.dragStart(handle, { dataTransfer: { setData: () => {}, effectAllowed: '' } });
    fireEvent.dragOver(items[2]);
    fireEvent.drop(items[2]);

    expect(await screen.findByText('C05 ahora es la pregunta 3 de 3')).toBeInTheDocument();
    expect(bodies[0]).toEqual({ capProyIds: [102, 103, 101], version: 4 });
  });

  it('showsServerRejectionWhenCrossingGroups', async () => {
    serveTree();
    server.use(
      http.patch(`${API_BASE}/pages/C1/order`, () =>
        problem(422, 'No se puede mover una pregunta a otro grupo.'),
      ),
    );
    renderApp(TREE_URL);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Bajar C05' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se puede mover una pregunta a otro grupo.',
    );
    expect(numeralsInOrder()).toEqual(['Pregunta C05', 'Pregunta C06', 'Pregunta C07A']);
  });
});
