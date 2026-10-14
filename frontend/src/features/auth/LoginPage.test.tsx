import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';

const RESPONDENT_SESSION = {
  accountType: 'RESPONDENT',
  roles: ['ENCUESTADO'],
  surveyCode: 'EDIN',
  surveyYear: 2026,
};

beforeEach(() => {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({ title: 'Sesión requerida', status: 401 }, { status: 401 }),
    ),
    http.get('*/api/v1/auth/csrf', () => HttpResponse.json({ token: 't' })),
    http.get('*/api/v1/survey/chapters', () =>
      HttpResponse.json([
        { code: 'B', title: 'Ubicación laboral', module: 'Información sociodemográfica' },
      ]),
    ),
  );
});

describe('LoginPage', () => {
  it('valida los campos obligatorios sin llamar al servidor', async () => {
    renderApp('/ingresar');
    await userEvent.click(await screen.findByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByText('Ingrese su usuario')).toBeInTheDocument();
    expect(screen.getByText('Ingrese su clave')).toBeInTheDocument();
    expect(screen.getByLabelText('Usuario')).toHaveAttribute('aria-invalid', 'true');
  });

  it('lleva al encuestado a la encuesta después de ingresar', async () => {
    let sentBody: unknown;
    server.use(
      http.post('*/api/v1/auth/login', async ({ request }) => {
        sentBody = await request.json();
        return HttpResponse.json(RESPONDENT_SESSION);
      }),
    );
    renderApp('/ingresar');

    await userEvent.type(await screen.findByLabelText('Usuario'), 'PRUEBA01');
    await userEvent.type(screen.getByLabelText('Clave'), 'Clave#01');
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByRole('heading', { name: 'Encuesta EDIN 2026' })).toBeInTheDocument();
    expect(sentBody).toEqual({
      accountType: 'RESPONDENT',
      username: 'PRUEBA01',
      password: 'Clave#01',
    });
  });

  it('muestra el mensaje del servidor cuando las credenciales fallan', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(
          { title: 'Credenciales inválidas', status: 401, detail: 'Usuario o clave incorrectos.' },
          { status: 401 },
        ),
      ),
    );
    renderApp('/ingresar');

    await userEvent.type(await screen.findByLabelText('Usuario'), 'PRUEBA01');
    await userEvent.type(screen.getByLabelText('Clave'), 'mala');
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Usuario o clave incorrectos.');
  });

  it('envía el tipo administrador cuando se elige', async () => {
    let sentBody: { accountType?: string } = {};
    server.use(
      http.post('*/api/v1/auth/login', async ({ request }) => {
        sentBody = (await request.json()) as { accountType?: string };
        return HttpResponse.json({
          ...RESPONDENT_SESSION,
          accountType: 'ADMINISTRATOR',
          roles: ['ADMIN_NACIONAL'],
        });
      }),
    );
    renderApp('/ingresar');

    await userEvent.click(await screen.findByRole('radio', { name: 'Administrador(a)' }));
    await userEvent.type(screen.getByLabelText('Usuario'), 'adm.nacional');
    await userEvent.type(screen.getByLabelText('Clave'), 'Nac#2026x');
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByRole('heading', { name: 'Administración' })).toBeInTheDocument();
    expect(sentBody.accountType).toBe('ADMINISTRATOR');
  });
});
