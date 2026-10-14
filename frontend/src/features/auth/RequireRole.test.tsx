import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';

function sessionResponse(roles: string[]) {
  return HttpResponse.json({
    accountType: roles.includes('ENCUESTADO') ? 'RESPONDENT' : 'ADMINISTRATOR',
    roles,
    surveyCode: 'EDIN',
    surveyYear: 2026,
  });
}

describe('RequireRole', () => {
  it('envía a ingresar a quien no tiene sesión', async () => {
    server.use(http.get('*/api/v1/auth/me', () => HttpResponse.json({}, { status: 401 })));
    renderApp('/encuesta');
    expect(
      await screen.findByRole('heading', { name: 'Ingreso a la encuesta' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Ingrese para continuar.');
  });

  it('niega la administración a un encuestado', async () => {
    server.use(http.get('*/api/v1/auth/me', () => sessionResponse(['ENCUESTADO'])));
    renderApp('/admin');
    expect(await screen.findByRole('heading', { name: 'Acceso denegado' })).toBeInTheDocument();
  });

  it('la raíz lleva al administrador a su módulo', async () => {
    server.use(http.get('*/api/v1/auth/me', () => sessionResponse(['ADMIN_TERRITORIAL'])));
    renderApp('/');
    expect(await screen.findByRole('heading', { name: 'Administración' })).toBeInTheDocument();
  });

  it('muestra un error recuperable si no se puede verificar la sesión', async () => {
    server.use(http.get('*/api/v1/auth/me', () => HttpResponse.json({}, { status: 503 })));
    renderApp('/encuesta');
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No fue posible verificar su sesión.',
    );
  });

  it('cerrar sesión vuelve a la pantalla de ingreso', async () => {
    let loggedIn = true;
    server.use(
      http.get('*/api/v1/auth/me', () =>
        loggedIn ? sessionResponse(['ADMIN_NACIONAL']) : HttpResponse.json({}, { status: 401 }),
      ),
      http.post('*/api/v1/auth/logout', () => {
        loggedIn = false;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderApp('/admin');

    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar sesión' }));

    expect(
      await screen.findByRole('heading', { name: 'Ingreso a la encuesta' }),
    ).toBeInTheDocument();
  });

  it('si el servidor falla al cerrar sesión, limpia la caché y avisa al usuario', async () => {
    server.use(
      http.get('*/api/v1/auth/me', () => sessionResponse(['ADMIN_NACIONAL'])),
      http.post('*/api/v1/auth/logout', () => HttpResponse.json({}, { status: 503 })),
    );
    const { queryClient } = renderApp('/admin');

    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar sesión' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se pudo cerrar la sesión en el servidor.',
    );
    expect(queryClient.getQueryData(['session'])).toBeNull();
  });
});
