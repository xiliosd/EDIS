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
        finished: true,
        observationsSaved: false,
        receipt: 'ABCDE-23456',
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
  it('muestra el radicado y envía las observaciones opcionales', async () => {
    useProgress({});
    let observationsSaved = false;
    server.use(
      http.get('*/api/v1/survey/progress', () =>
        HttpResponse.json({
          pages: SAVED_PAGES,
          finished: true,
          observationsSaved,
          receipt: 'ABCDE-23456',
        }),
      ),
      http.post('*/api/v1/survey/finish', async ({ request }) => {
        finishBody = await request.json();
        observationsSaved = true;
        return HttpResponse.json({ receipt: 'ABCDE-23456' });
      }),
    );
    renderApp('/encuesta/terminar');
    expect(await screen.findByText('ABCDE-23456')).toBeInTheDocument();
    expect(screen.getByRole('status')).not.toHaveFocus();
    const field = screen.getByLabelText('Observaciones (opcional)');
    expect(field).toHaveAttribute('maxlength', '1100');

    await userEvent.type(field, 'Gracias');
    expect(screen.getByText('7 de 1100 caracteres')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Enviar observaciones' }));

    expect(await screen.findByText('Sus observaciones ya fueron registradas.')).toBeInTheDocument();
    expect(finishBody).toEqual({ observations: 'Gracias' });
    expect(screen.queryByLabelText('Observaciones (opcional)')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('ABCDE-23456');
    expect(screen.getByRole('status')).toHaveFocus();
  });

  it('no permite terminar con páginas pendientes', async () => {
    useProgress({
      pages: [{ ...SAVED_PAGES[0], state: 'AVAILABLE' }],
      finished: false,
      receipt: null,
    });
    renderApp('/encuesta/terminar');

    expect(await screen.findByText('Faltan páginas por guardar.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enviar observaciones' })).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('con las observaciones registradas muestra el radicado y no el formulario', async () => {
    useProgress({ observationsSaved: true });
    renderApp('/encuesta/terminar');

    expect(await screen.findByText('ABCDE-23456')).toBeInTheDocument();
    expect(screen.getByText('Sus observaciones ya fueron registradas.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Observaciones (opcional)')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enviar observaciones' })).not.toBeInTheDocument();
  });

  it('sin observaciones muestra el radicado junto con el formulario opcional', async () => {
    useProgress({});
    renderApp('/encuesta/terminar');

    expect(await screen.findByText('ABCDE-23456')).toBeInTheDocument();
    expect(screen.getByLabelText('Observaciones (opcional)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar observaciones' })).toBeInTheDocument();
  });

  it('no permite enviar observaciones vacías o solo con espacios', async () => {
    useProgress({});
    renderApp('/encuesta/terminar');
    const field = await screen.findByLabelText('Observaciones (opcional)');
    const button = screen.getByRole('button', { name: 'Enviar observaciones' });
    expect(button).toBeDisabled();

    await userEvent.type(field, '   ');
    expect(button).toBeDisabled();
    await userEvent.type(field, 'x');
    expect(button).toBeEnabled();
    expect(finishBody).toBeUndefined();
  });

  it('avisa que la sesión venció al enviar', async () => {
    useProgress({});
    server.use(
      http.post('*/api/v1/survey/finish', () =>
        HttpResponse.json({ title: 'No autenticado', status: 401 }, { status: 401 }),
      ),
    );
    renderApp('/encuesta/terminar');
    await userEvent.type(await screen.findByLabelText('Observaciones (opcional)'), 'Nota');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar observaciones' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Su sesión venció. Vuelva a ingresar');
    expect(screen.getByRole('link', { name: 'Ingresar de nuevo' })).toHaveAttribute(
      'href',
      '/ingresar',
    );
  });

  it('pide recargar si el token CSRF ya no corresponde', async () => {
    useProgress({});
    server.use(
      http.post('*/api/v1/survey/finish', () =>
        HttpResponse.json({ title: 'Prohibido', status: 403 }, { status: 403 }),
      ),
    );
    renderApp('/encuesta/terminar');
    await userEvent.type(await screen.findByLabelText('Observaciones (opcional)'), 'Nota');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar observaciones' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La sesión de seguridad cambió. Recargue la página e intente de nuevo.',
    );
    expect(screen.queryByRole('link', { name: 'Ingresar de nuevo' })).not.toBeInTheDocument();
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
    await userEvent.type(await screen.findByLabelText('Observaciones (opcional)'), 'Nota');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar observaciones' }));

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
    await userEvent.click(screen.getByRole('button', { name: 'Enviar observaciones' }));

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
    await userEvent.type(field, 'Nota');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar observaciones' }));
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
