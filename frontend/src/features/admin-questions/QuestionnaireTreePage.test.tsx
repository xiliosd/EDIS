import { READ_ONLY_NOTE } from '../../components/ReadOnlyNotice';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { readOnlyEnvironment, server } from '../../test/msw-server';
import {
  API_BASE,
  draftView,
  entry,
  PAGE_C1,
  problem,
  serveTree,
  TREE_URL,
} from '../../test/questionnaire-fixtures';
import { renderApp } from '../../test/render-app';

describe('QuestionnaireTreePage', () => {
  it('showsPagesWithEntryDetails', async () => {
    serveTree();
    renderApp(TREE_URL);

    expect(await screen.findByRole('heading', { name: 'Página C1' })).toBeInTheDocument();
    expect(screen.getByText('Capítulo C · Tabla EDIN_27_CAP_C')).toBeInTheDocument();
    const first = screen.getByRole('listitem', { name: 'Pregunta C05' });
    expect(within(first).getByText('Columna C05')).toBeInTheDocument();
    expect(within(first).getByText('Opción única')).toBeInTheDocument();
    expect(within(first).getByText('Activa')).toBeInTheDocument();
    expect(within(first).getByText('Usada por 1 regla: salto-c05')).toBeInTheDocument();
    expect(within(first).getByText('Ambiente laboral')).toBeInTheDocument();
    expect(within(first).getByText('último año')).toBeInTheDocument();
    expect(
      within(first).getByText('Año calendario anterior al de la encuesta.'),
    ).toBeInTheDocument();
    // La vista previa muestra el año sustituido; el texto crudo con 200X se edita.
    expect(within(first).getByText('¿En 2027 trabajó en la entidad?')).toBeInTheDocument();
    const pending = screen.getByRole('listitem', { name: 'Pregunta C07A' });
    expect(within(pending).getByText('Pendiente de columna')).toBeInTheDocument();
    expect(within(pending).getByText('Texto')).toBeInTheDocument();
    expect(within(pending).getByText('Usada por 0 reglas')).toBeInTheDocument();
  });

  it('textIsStoredPlainAndRenderedEscaped', async () => {
    const markup = '<script>alert(1)</script> &lt;b&gt;';
    serveTree(
      draftView({
        pages: [
          {
            ...PAGE_C1,
            entries: [
              entry({
                capProyId: 101,
                numeral: 'C05',
                text: markup,
                rawText: markup,
                headers: [{ codEnc: 501, kind: 'HEADING', text: '<b>Encabezado</b>' }],
                help: {
                  codPreAyu: 701,
                  codAyu: 9001,
                  text: '<img src=x onerror=alert(1)>',
                  definition: { title: '<b>Título</b>', excerpt: '<script>alert(1)</script>' },
                },
              }),
            ],
          },
        ],
      }),
    );
    const { container } = renderApp(TREE_URL);

    expect(await screen.findByText(markup)).toBeInTheDocument();
    expect(screen.getByText('<b>Encabezado</b>')).toBeInTheDocument();
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(screen.getByText('<b>Título</b>')).toBeInTheDocument();
    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();
    expect(container.querySelector('main script')).toBeNull();
    expect(container.querySelector('main img')).toBeNull();
    expect(container.querySelector('main b')).toBeNull();
  });

  it('publishedYearDisablesStructuralControlsAndOffersCorrections', async () => {
    serveTree(draftView({ state: 'publicado' }));
    renderApp(TREE_URL);

    expect(await screen.findByRole('textbox', { name: 'Motivo de la corrección' })).toBeRequired();
    expect(screen.getByRole('heading', { name: 'Página C1' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Desactivar C05/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Agregar pregunta en C1' })).toBeDisabled();
    for (const name of [/^Subir /, /^Bajar /, 'Guardar tablas']) {
      for (const button of screen.getAllByRole('button', { name })) {
        expect(button).toBeDisabled();
        expect(button).toHaveAccessibleDescription('Solo en borrador.');
      }
    }
    expect(screen.getByRole('button', { name: /^Desactivar C05/ })).toHaveAccessibleDescription(
      'Solo en borrador.',
    );
    expect(
      screen.getByRole('button', { name: 'Agregar pregunta en C1' }),
    ).toHaveAccessibleDescription('Solo en borrador.');
    // Los textos sí se corrigen.
    expect(screen.getByRole('button', { name: 'Editar texto de C05' })).toBeEnabled();
    expect(screen.getByText('EDIN_27_CAP_H')).toBeInTheDocument();
  });

  it('publishedYearBlocksSaveWithoutReasonAndSendsTrimmedReason', async () => {
    const bodies: unknown[] = [];
    serveTree(draftView({ state: 'publicado' }));
    server.use(
      http.put(`${API_BASE}/entries/101/text`, async ({ request }) => {
        const body = (await request.json()) as { text: string };
        bodies.push(body);
        return HttpResponse.json({
          version: 5,
          page: {
            ...PAGE_C1,
            entries: [
              entry({ capProyId: 101, numeral: 'C05', text: body.text, rawText: body.text }),
            ],
          },
          skipsTargetingEarlierQuestions: [],
        });
      }),
    );
    renderApp(TREE_URL);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Editar texto de C05' }));
    await user.type(screen.getByLabelText('Texto de C05'), ' ajustado');
    expect(screen.getByRole('button', { name: 'Guardar' })).toBeDisabled();

    const reason = screen.getByRole('textbox', { name: 'Motivo de la corrección' });
    await user.type(reason, '  Se corrige la redacción  ');
    expect(screen.getByRole('button', { name: 'Guardar' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await screen.findByRole('button', { name: 'Editar texto de C05' });
    expect(bodies).toEqual([
      {
        text: '¿En 200X trabajó en la entidad? ajustado',
        version: 4,
        motivo: 'Se corrige la redacción',
      },
    ]);
    // El motivo se vacía tras guardar: la siguiente corrección necesita el suyo.
    expect(reason).toHaveValue('');
  });

  it('showsLoadErrorWithRetry', async () => {
    serveTree();
    server.use(http.get(API_BASE, () => problem(404, 'No existe el cuestionario de ese año.')));
    renderApp(TREE_URL);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No existe el cuestionario de ese año.',
    );
    server.use(http.get(API_BASE, () => HttpResponse.json(draftView())));
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('heading', { name: 'Página C1' })).toBeInTheDocument();
  });
});

describe('QuestionnaireTreePage en un ambiente de solo lectura', () => {
  it('readOnlyEnvironmentDisablesEveryWriteControlAndLinksTheNotice', async () => {
    readOnlyEnvironment();
    serveTree();
    renderApp(TREE_URL);

    expect(await screen.findByRole('note')).toHaveTextContent(READ_ONLY_NOTE);
    expect(screen.getAllByRole('note')).toHaveLength(1);
    const writes = [
      'Editar texto de C05',
      'Editar encabezado de C05',
      'Editar palabra enlazada de C05',
      /^Desactivar C05/,
      'Agregar pregunta en C1',
      'Guardar tablas',
      /^Subir /,
      /^Bajar /,
    ];
    for (const name of writes) {
      for (const button of screen.getAllByRole('button', { name })) {
        expect(button).toBeDisabled();
        expect(button).toHaveAccessibleDescription(READ_ONLY_NOTE);
      }
    }
    // Las lecturas siguen disponibles: el script de columna solo se descarga.
    expect(
      screen.getByRole('button', { name: 'Descargar script de columna de C07A' }),
    ).toBeEnabled();
    expect(screen.queryByText('Solo en borrador.')).not.toBeInTheDocument();
  });

  it('readOnlyPublishedYearHidesTheCorrectionReason', async () => {
    readOnlyEnvironment();
    serveTree(draftView({ state: 'publicado' }));
    renderApp(TREE_URL);

    expect(await screen.findByRole('note')).toHaveTextContent(READ_ONLY_NOTE);
    expect(screen.getByRole('button', { name: 'Editar texto de C05' })).toBeDisabled();
    expect(
      screen.queryByRole('textbox', { name: 'Motivo de la corrección' }),
    ).not.toBeInTheDocument();
  });

  it('writableEnvironmentShowsNoNotice', async () => {
    serveTree();
    renderApp(TREE_URL);

    expect(await screen.findByRole('button', { name: 'Editar texto de C05' })).toBeEnabled();
    expect(screen.queryByText(READ_ONLY_NOTE)).not.toBeInTheDocument();
  });
});
