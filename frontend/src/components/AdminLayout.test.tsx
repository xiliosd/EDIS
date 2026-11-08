import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/msw-server';
import { renderApp } from '../test/render-app';

function serve(roles: string[], scope: { canManageNovelties: boolean; canManageKeys: boolean }) {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({ accountType: 'ADMINISTRATOR', roles, departmentCode: '5' }),
    ),
    http.get('*/api/v1/admin/operation/scope', () =>
      HttpResponse.json({ allDepartments: false, defaultYear: 2026, departments: [], ...scope }),
    ),
  );
}

async function openMenu() {
  return screen.findByRole('navigation', { name: 'Administración' });
}

const REPORT_LINKS = [
  'Cobertura',
  'Cortes e histórico',
  'Estado del operativo',
  'Novedades',
  'Rendimientos',
];

describe('AdminLayout según el rol', () => {
  it('showsTheReportsGroupToOperationRoles', async () => {
    for (const role of ['ADMIN_NACIONAL', 'ADMIN_TERRITORIAL', 'ADMIN_OPERATIVO']) {
      serve([role], { canManageNovelties: false, canManageKeys: false });
      const view = renderApp('/admin');
      const nav = await openMenu();
      const group = await within(nav).findByRole('list', { name: 'Reportes' });
      for (const name of REPORT_LINKS) {
        expect(within(group).getByRole('link', { name })).toBeInTheDocument();
      }
      expect(within(nav).getByRole('link', { name: 'Cobertura' })).toHaveAttribute(
        'href',
        '/admin/reportes/cobertura',
      );
      view.unmount();
    }
  });

  it('hidesReportsAndManagementFromTheQuestionnaireAdministrator', async () => {
    serve(['ADMIN_CUESTIONARIO'], { canManageNovelties: true, canManageKeys: true });
    renderApp('/admin');
    const nav = await openMenu();

    expect(await within(nav).findByRole('link', { name: 'Cuestionario' })).toBeInTheDocument();
    expect(within(nav).queryByRole('list', { name: 'Reportes' })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Claves' })).not.toBeInTheDocument();
    expect(
      within(nav).queryByRole('link', { name: 'Gestión de novedades' }),
    ).not.toBeInTheDocument();
  });

  it('showsNoveltiesManagementOnlyWhenTheScopeAllowsIt', async () => {
    serve(['ADMIN_TERRITORIAL'], { canManageNovelties: true, canManageKeys: false });
    renderApp('/admin');
    const nav = await openMenu();

    expect(await within(nav).findByRole('link', { name: 'Gestión de novedades' })).toHaveAttribute(
      'href',
      '/admin/novedades',
    );
    expect(within(nav).queryByRole('link', { name: 'Claves' })).not.toBeInTheDocument();
  });

  it('showsKeysOnlyWhenTheScopeAllowsIt', async () => {
    serve(['ADMIN_NACIONAL'], { canManageNovelties: false, canManageKeys: true });
    renderApp('/admin');
    const nav = await openMenu();

    expect(await within(nav).findByRole('link', { name: 'Claves' })).toHaveAttribute(
      'href',
      '/admin/claves',
    );
    expect(
      within(nav).queryByRole('link', { name: 'Gestión de novedades' }),
    ).not.toBeInTheDocument();
  });
});
