import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';

describe('SectionPlaceholderPage', () => {
  it.each(['Historial', 'Paquetes'])('%s anuncia que estará disponible pronto', async (section) => {
    server.use(
      http.get('*/api/v1/auth/me', () =>
        HttpResponse.json({
          accountType: 'ADMINISTRATOR',
          roles: ['ADMIN_CUESTIONARIO'],
          departmentCode: '11',
        }),
      ),
    );
    renderApp(`/admin/cuestionario/EDIN/2027/${section.toLowerCase()}`);

    expect(await screen.findByRole('heading', { name: `${section} EDIN 2027` })).toBeVisible();
    expect(screen.getByText('Disponible próximamente.')).toBeVisible();
  });
});
