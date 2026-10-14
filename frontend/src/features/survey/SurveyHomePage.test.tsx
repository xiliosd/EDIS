import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';

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
  it('lista los capítulos con su módulo', async () => {
    server.use(
      http.get('*/api/v1/survey/chapters', () =>
        HttpResponse.json([
          { code: 'B', title: 'Ubicación laboral', module: 'Información sociodemográfica' },
          { code: 'C', title: 'Experiencia personal de trabajo', module: 'Ambiente institucional' },
        ]),
      ),
    );
    renderApp('/encuesta');

    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('B. Ubicación laboral');
    expect(items[0]).toHaveTextContent('Información sociodemográfica');
  });

  it('permite reintentar cuando falla la carga', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/v1/survey/chapters', () => {
        calls += 1;
        return calls === 1
          ? HttpResponse.json({ title: 'Servicio no disponible', status: 503 }, { status: 503 })
          : HttpResponse.json([
              { code: 'B', title: 'Ubicación laboral', module: 'Información sociodemográfica' },
            ]);
      }),
    );
    renderApp('/encuesta');

    await userEvent.click(await screen.findByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('B. Ubicación laboral')).toBeInTheDocument();
  });
});
