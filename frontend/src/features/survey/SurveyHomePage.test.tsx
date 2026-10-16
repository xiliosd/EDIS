import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';

const PAGES = [
  {
    pageCode: 'B1',
    chapterCode: 'B',
    chapterTitle: 'Ubicación laboral',
    module: 'Información sociodemográfica',
    state: 'SAVED',
  },
  {
    pageCode: 'C1',
    chapterCode: 'C',
    chapterTitle: 'Experiencia personal de trabajo',
    module: 'Ambiente institucional',
    state: 'AVAILABLE',
  },
  {
    pageCode: 'C2',
    chapterCode: 'C',
    chapterTitle: 'Experiencia personal de trabajo',
    module: 'Ambiente institucional',
    state: 'LOCKED',
  },
];

function progress(overrides: Record<string, unknown> = {}) {
  return { pages: PAGES, finished: false, observationsSaved: false, receipt: null, ...overrides };
}

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
  );
});

describe('SurveyHomePage', () => {
  it('muestra cada página con su estado en texto y el botón para continuar', async () => {
    server.use(http.get('*/api/v1/survey/progress', () => HttpResponse.json(progress())));
    renderApp('/encuesta');

    expect(await screen.findByRole('heading', { name: 'Encuesta EDIN 2026' })).toBeInTheDocument();
    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(3);
    expect(within(items[0]).getByText('B. Ubicación laboral')).toBeInTheDocument();
    expect(items[0]).toHaveTextContent('Guardada');
    expect(items[1]).toHaveTextContent('Parte 1 de 2');
    expect(items[1]).toHaveTextContent('Disponible');
    expect(items[2]).toHaveTextContent('Bloqueada');
    expect(screen.getByRole('link', { name: 'Continuar' })).toHaveAttribute(
      'href',
      '/encuesta/pagina/C1',
    );
    expect(document.title).toBe('Encuesta EDIN 2026 · Encuesta EDI');
  });

  it('ofrece terminar cuando todas las páginas están guardadas', async () => {
    const saved = PAGES.map((page) => ({ ...page, state: 'SAVED' }));
    server.use(
      http.get('*/api/v1/survey/progress', () => HttpResponse.json(progress({ pages: saved }))),
    );
    renderApp('/encuesta');

    expect(await screen.findByRole('link', { name: 'Terminar la encuesta' })).toHaveAttribute(
      'href',
      '/encuesta/terminar',
    );
  });

  it('muestra el radicado cuando la encuesta ya terminó', async () => {
    const saved = PAGES.map((page) => ({ ...page, state: 'SAVED' }));
    server.use(
      http.get('*/api/v1/survey/progress', () =>
        HttpResponse.json(
          progress({
            pages: saved,
            finished: true,
            observationsSaved: true,
            receipt: 'ABCDE-23456',
          }),
        ),
      ),
    );
    renderApp('/encuesta');

    expect(await screen.findByText('ABCDE-23456')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Continuar' })).not.toBeInTheDocument();
  });

  it('permite reintentar cuando falla la carga', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/v1/survey/progress', () => {
        calls += 1;
        return calls === 1
          ? HttpResponse.json({ title: 'Servicio no disponible', status: 503 }, { status: 503 })
          : HttpResponse.json(progress());
      }),
    );
    renderApp('/encuesta');

    await userEvent.click(await screen.findByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('B. Ubicación laboral')).toBeInTheDocument();
  });
});
