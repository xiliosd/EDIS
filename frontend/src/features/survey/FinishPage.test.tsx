import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';

const SAVED_PAGES = [
  {
    pageCode: 'B1',
    chapterCode: 'B',
    chapterTitle: 'Ubicación laboral',
    module: 'M',
    state: 'SAVED',
  },
];

let finishBody: unknown;

function useProgress(overrides: Record<string, unknown>) {
  server.use(
    http.get('*/api/v1/survey/progress', () =>
      HttpResponse.json({
        pages: SAVED_PAGES,
        finished: false,
        observationsSaved: false,
        receipt: null,
        ...overrides,
      }),
    ),
  );
}

beforeEach(() => {
  finishBody = undefined;
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
    http.post('*/api/v1/survey/finish', async ({ request }) => {
      finishBody = await request.json();
      return HttpResponse.json({ receipt: 'ABCDE-23456' });
    }),
  );
});

describe('FinishPage', () => {
  it('envía las observaciones y muestra el radicado', async () => {
    useProgress({});
    renderApp('/encuesta/terminar');
    const field = await screen.findByLabelText('Observaciones (opcional)');
    expect(field).toHaveAttribute('maxlength', '1100');

    await userEvent.type(field, 'Gracias');
    expect(screen.getByText('7 de 1100 caracteres')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Terminar la encuesta' }));

    expect(await screen.findByRole('status')).toHaveTextContent('ABCDE-23456');
    expect(finishBody).toEqual({ observations: 'Gracias' });
    expect(screen.queryByLabelText('Observaciones (opcional)')).not.toBeInTheDocument();
    expect(await screen.findByRole('status')).toHaveFocus();
  });

  it('no permite terminar con páginas pendientes', async () => {
    useProgress({ pages: [{ ...SAVED_PAGES[0], state: 'AVAILABLE' }] });
    renderApp('/encuesta/terminar');

    expect(await screen.findByText('Faltan páginas por guardar.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Terminar la encuesta' })).not.toBeInTheDocument();
  });

  it('muestra el radicado si la encuesta ya había terminado', async () => {
    useProgress({ finished: true, observationsSaved: true, receipt: 'ABCDE-23456' });
    renderApp('/encuesta/terminar');

    expect(await screen.findByText('ABCDE-23456')).toBeInTheDocument();
    expect(screen.queryByLabelText('Observaciones (opcional)')).not.toBeInTheDocument();
  });

  it('ofrece las observaciones aunque el backend ya informe todas las páginas guardadas', async () => {
    useProgress({ finished: true, observationsSaved: false, receipt: 'ABCDE-23456' });
    renderApp('/encuesta/terminar');

    expect(await screen.findByLabelText('Observaciones (opcional)')).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('oculta el campo si las observaciones ya fueron registradas', async () => {
    useProgress({ observationsSaved: true });
    renderApp('/encuesta/terminar');

    expect(await screen.findByText('Sus observaciones ya fueron registradas.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Observaciones (opcional)')).not.toBeInTheDocument();
  });

  it('muestra el conflicto que informa el backend', async () => {
    useProgress({});
    server.use(
      http.post('*/api/v1/survey/finish', () =>
        HttpResponse.json(
          { title: 'Conflicto', status: 409, detail: 'Faltan páginas por guardar.' },
          { status: 409 },
        ),
      ),
    );
    renderApp('/encuesta/terminar');
    await userEvent.click(await screen.findByRole('button', { name: 'Terminar la encuesta' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Faltan páginas por guardar.');
  });

  it('asocia al campo el error de observaciones demasiado largas', async () => {
    const message = 'Las observaciones superan el espacio disponible. Acórtelas.';
    useProgress({});
    server.use(
      http.post('*/api/v1/survey/finish', () =>
        HttpResponse.json(
          {
            title: 'Datos inválidos',
            status: 422,
            detail: 'Revise los datos.',
            errors: { observations: message },
          },
          { status: 422 },
        ),
      ),
    );
    renderApp('/encuesta/terminar');
    await userEvent.type(await screen.findByLabelText('Observaciones (opcional)'), 'ñ');
    await userEvent.click(screen.getByRole('button', { name: 'Terminar la encuesta' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(message);
    const field = screen.getByLabelText('Observaciones (opcional)');
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field.getAttribute('aria-describedby')).toContain(alert.id);
  });

  it('limpia el error al editar el campo', async () => {
    useProgress({});
    server.use(
      http.post('*/api/v1/survey/finish', () =>
        HttpResponse.json({ title: 'Conflicto', status: 409, detail: 'Falla.' }, { status: 409 }),
      ),
    );
    renderApp('/encuesta/terminar');
    const field = await screen.findByLabelText('Observaciones (opcional)');
    await userEvent.click(screen.getByRole('button', { name: 'Terminar la encuesta' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await userEvent.type(field, 'a');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('actualiza el título del documento', async () => {
    useProgress({});
    renderApp('/encuesta/terminar');
    await screen.findByLabelText('Observaciones (opcional)');
    expect(document.title).toBe('Terminar la encuesta · Encuesta EDI');
  });
});
