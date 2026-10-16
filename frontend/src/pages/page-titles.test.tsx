import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/msw-server';
import { renderApp } from '../test/render-app';

describe('títulos de documento (WCAG 2.4.2)', () => {
  it('la página de ingreso lleva su título', async () => {
    server.use(http.get('*/api/v1/auth/me', () => HttpResponse.json({}, { status: 401 })));
    renderApp('/ingresar');
    await screen.findByRole('heading', { name: 'Ingreso a la encuesta' });
    await waitFor(() => expect(document.title).toBe('Ingreso a la encuesta · Encuesta EDI'));
  });

  it('la administración lleva su título', async () => {
    server.use(
      http.get('*/api/v1/auth/me', () =>
        HttpResponse.json({
          accountType: 'ADMINISTRATOR',
          roles: ['ADMIN_NACIONAL'],
          surveyCode: 'EDIN',
          surveyYear: 2026,
        }),
      ),
    );
    renderApp('/admin');
    await screen.findByRole('heading', { name: 'Administración' });
    await waitFor(() => expect(document.title).toBe('Administración · Encuesta EDI'));
  });

  it('la página inexistente lleva su título', async () => {
    server.use(http.get('*/api/v1/auth/me', () => HttpResponse.json({}, { status: 401 })));
    renderApp('/no-existe');
    await screen.findByRole('heading', { name: 'Página no encontrada' });
    await waitFor(() => expect(document.title).toBe('Página no encontrada · Encuesta EDI'));
  });
});
