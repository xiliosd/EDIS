import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';
import type { NoveltyPerson } from './novelties-api';

const PAGE_URL = '/admin/novedades';
const PROBLEM = { 'Content-Type': 'application/problem+json' };
const OPTIONS = { rules: { 'color-contrast': { enabled: false } } };

const SCOPE = {
  allDepartments: false,
  canManageKeys: false,
  canManageNovelties: true,
  defaultYear: 2026,
  departments: [
    { code: 5, name: 'Antioquia' },
    { code: 8, name: 'Atlántico' },
  ],
};

const CATALOG = [
  { code: 3, name: 'Retiro', requiresText: false },
  { code: 8, name: 'Otra causa', requiresText: true },
];

const ENTITIES = [{ code: 900001, name: 'Entidad Ficticia Uno', sampleSize: 3, withNovelty: 1 }];

const WITH_NOVELTY: NoveltyPerson = {
  level: 1,
  name: 'Persona Ficticia Uno',
  noveltyCode: 3,
  respondentCode: '2000001',
  status: 'NOVELTY',
};
const WITHOUT_NOVELTY: NoveltyPerson = {
  level: 1,
  name: 'Persona Ficticia Dos',
  respondentCode: '2000002',
  status: 'NOT_STARTED',
};
const WITH_OTHER_CAUSE: NoveltyPerson = {
  level: 2,
  name: 'Persona Ficticia Tres',
  noveltyCode: 8,
  otherCause: 'Licencia “ficticia”',
  respondentCode: '2000003',
  status: 'NOVELTY',
};

interface Calls {
  assign: unknown[];
  remove: unknown[];
  peopleRequests: unknown[];
  urls: string[];
}

function serve(options: { people?: NoveltyPerson[]; scope?: typeof SCOPE } = {}) {
  const calls: Calls = { assign: [], remove: [], peopleRequests: [], urls: [] };
  const people = options.people ?? [WITH_NOVELTY, WITHOUT_NOVELTY, WITH_OTHER_CAUSE];
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({
        accountType: 'ADMINISTRATOR',
        roles: ['ADMIN_TERRITORIAL'],
        departmentCode: '5',
      }),
    ),
    http.get('*/api/v1/auth/csrf', () => HttpResponse.json({ token: 'x' })),
    http.get('*/api/v1/admin/operation/scope', () => HttpResponse.json(options.scope ?? SCOPE)),
    http.get('*/api/v1/admin/reports/years', () => HttpResponse.json([2026])),
    http.get('*/api/v1/admin/novelties/catalog', ({ request }) => {
      calls.urls.push(request.url);
      return HttpResponse.json(CATALOG);
    }),
    http.get('*/api/v1/admin/novelties/entities', ({ request }) => {
      calls.urls.push(request.url);
      return HttpResponse.json(ENTITIES);
    }),
    http.post('*/api/v1/admin/novelties/people', async ({ request }) => {
      calls.urls.push(request.url);
      calls.peopleRequests.push(await request.json());
      return HttpResponse.json(people);
    }),
    http.post('*/api/v1/admin/novelties/assign', async ({ request }) => {
      calls.urls.push(request.url);
      calls.assign.push(await request.json());
      return new HttpResponse(null, { status: 204 });
    }),
    http.post('*/api/v1/admin/novelties/remove', async ({ request }) => {
      calls.urls.push(request.url);
      calls.remove.push(await request.json());
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return calls;
}

async function openList(expectTable = true) {
  renderApp(PAGE_URL);
  await screen.findByRole('heading', { name: 'Gestión de novedades' });
  const user = userEvent.setup();
  await user.selectOptions(await screen.findByLabelText('Departamento'), '5');
  const entity = await screen.findByLabelText('Entidad');
  await waitFor(() => expect(within(entity).getAllByRole('option')).toHaveLength(2));
  await user.selectOptions(entity, '900001');
  if (expectTable) {
    await screen.findByRole('table', { name: 'Funcionarios de la entidad y su novedad' });
  }
  return user;
}

function row(name: string) {
  return screen.getByRole('row', { name: new RegExp(name) });
}

describe('NoveltiesPage', () => {
  it('listsThePeopleWithStatusAndNoveltyAndKeepsTheIdNumberOutOfUrls', async () => {
    const calls = serve();
    await openList();

    expect(within(row('Persona Ficticia Uno')).getByText('Con novedad')).toBeInTheDocument();
    expect(within(row('Persona Ficticia Uno')).getByText('Retiro')).toBeInTheDocument();
    expect(within(row('Persona Ficticia Dos')).getByText('Sin iniciar')).toBeInTheDocument();
    expect(
      within(row('Persona Ficticia Tres')).getByText('Otra causa: Licencia “ficticia”'),
    ).toBeInTheDocument();
    expect(calls.peopleRequests).toEqual([{ departamento: 5, entidad: 900001 }]);
    expect(calls.urls.some((url) => /200000\d/.test(url))).toBe(false);
    expect(document.title).not.toMatch(/200000\d/);
  });

  it('assignsANoveltyWithoutSendingExpectedValuesForAPersonWithoutOne', async () => {
    const calls = serve();
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Asignar novedad de Persona Ficticia Dos' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /Asignar novedad de/ });
    await user.selectOptions(within(dialog).getByLabelText('Novedad'), '3');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(calls.assign).toHaveLength(1));
    expect(calls.assign[0]).toEqual({ respondentCode: '2000002', noveltyCode: 3 });
    // La lista se recarga tras guardar.
    await waitFor(() => expect(calls.peopleRequests).toHaveLength(2));
  });

  it('changesANoveltySendingTheValuesReadOnScreenAsExpected', async () => {
    const calls = serve();
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Cambiar novedad de Persona Ficticia Tres' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /Cambiar novedad de/ });
    expect(within(dialog).getByLabelText('Otra causa')).toHaveValue('Licencia “ficticia”');
    await user.selectOptions(within(dialog).getByLabelText('Novedad'), '3');
    expect(within(dialog).queryByLabelText('Otra causa')).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(calls.assign).toHaveLength(1));
    expect(calls.assign[0]).toEqual({
      respondentCode: '2000003',
      noveltyCode: 3,
      expectedNoveltyCode: 8,
      expectedOtherCause: 'Licencia “ficticia”',
    });
  });

  it('doesNotSendOtherCauseWithoutTextAndShowsTheErrorNextToTheField', async () => {
    const calls = serve();
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Asignar novedad de Persona Ficticia Dos' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /Asignar novedad de/ });
    await user.selectOptions(within(dialog).getByLabelText('Novedad'), '8');
    await user.type(within(dialog).getByLabelText('Otra causa'), '   ');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    const field = within(dialog).getByLabelText('Otra causa');
    expect(field).toHaveAccessibleDescription(/Escriba la otra causa/);
    expect(field).toHaveFocus();
    expect(calls.assign).toHaveLength(0);
  });

  it('blocksAnOtherCauseOf501BytesAndAcceptsOneOf500', async () => {
    const calls = serve();
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Asignar novedad de Persona Ficticia Dos' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /Asignar novedad de/ });
    await user.selectOptions(within(dialog).getByLabelText('Novedad'), '8');
    const field = within(dialog).getByLabelText('Otra causa');
    await user.click(field);
    await user.paste('a'.repeat(501));
    expect(field).toHaveAccessibleDescription(/501 de 500 bytes/);
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));
    expect(field).toHaveAccessibleDescription(/501 bytes y el máximo es 500/);
    expect(calls.assign).toHaveLength(0);

    await user.clear(field);
    await user.paste('a'.repeat(500));
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));
    await waitFor(() => expect(calls.assign).toHaveLength(1));
  });

  it('countsCharactersOutsideLatin9WithTheSpaceOfTheirEntity', async () => {
    serve();
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Asignar novedad de Persona Ficticia Dos' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /Asignar novedad de/ });
    await user.selectOptions(within(dialog).getByLabelText('Novedad'), '8');
    await user.type(within(dialog).getByLabelText('Otra causa'), 'ñ“');

    // ñ ocupa 1 byte y “ se guarda como &#8220; (7 caracteres).
    expect(within(dialog).getByLabelText('Otra causa')).toHaveAccessibleDescription(
      '8 de 500 bytes',
    );
  });

  it('requiresANoveltyBeforeSaving', async () => {
    const calls = serve();
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Asignar novedad de Persona Ficticia Dos' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /Asignar novedad de/ });
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    expect(within(dialog).getByLabelText('Novedad')).toHaveAccessibleDescription(
      'Seleccione una novedad.',
    );
    expect(calls.assign).toHaveLength(0);
  });

  it('showsTheServerValidationNextToTheField', async () => {
    serve();
    server.use(
      http.post('*/api/v1/admin/novelties/assign', () =>
        HttpResponse.json(
          {
            title: 'Datos inválidos',
            detail: 'Revise',
            errors: { otherCause: 'Texto no válido.' },
          },
          { status: 422, headers: PROBLEM },
        ),
      ),
    );
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Asignar novedad de Persona Ficticia Dos' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /Asignar novedad de/ });
    await user.selectOptions(within(dialog).getByLabelText('Novedad'), '8');
    await user.type(within(dialog).getByLabelText('Otra causa'), 'Algo');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    await waitFor(() =>
      expect(within(dialog).getByLabelText('Otra causa')).toHaveAccessibleDescription(
        /Texto no válido/,
      ),
    );
  });

  it('showsAGeneralServerErrorInsideTheEditor', async () => {
    serve();
    server.use(
      http.post('*/api/v1/admin/novelties/assign', () =>
        HttpResponse.json(
          { title: 'No encontrado', detail: 'No se encontró el funcionario.' },
          { status: 404, headers: PROBLEM },
        ),
      ),
    );
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Asignar novedad de Persona Ficticia Dos' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /Asignar novedad de/ });
    await user.selectOptions(within(dialog).getByLabelText('Novedad'), '3');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'No se encontró el funcionario.',
    );
  });

  it('showsAConflictNoticeAndReloadsTheListOnA409', async () => {
    const calls = serve();
    server.use(
      http.post('*/api/v1/admin/novelties/assign', () =>
        HttpResponse.json(
          { title: 'Conflicto', detail: 'Cambió.' },
          { status: 409, headers: PROBLEM },
        ),
      ),
    );
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Cambiar novedad de Persona Ficticia Uno' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /Cambiar novedad de/ });
    await user.selectOptions(within(dialog).getByLabelText('Novedad'), '8');
    await user.type(within(dialog).getByLabelText('Otra causa'), 'Motivo');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));

    const notice = await screen.findByRole('alert');
    expect(notice).toHaveTextContent(/cambió mientras la editaba/);
    await waitFor(() => expect(calls.peopleRequests).toHaveLength(2));
    expect(screen.queryByRole('dialog', { name: /Cambiar novedad/ })).not.toBeInTheDocument();

    await user.click(within(notice).getByRole('button', { name: 'Recargar' }));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    expect(calls.peopleRequests.length).toBeGreaterThanOrEqual(3);
  });

  it('asksForConfirmationBeforeRemovingAndSendsTheExpectedValues', async () => {
    const calls = serve();
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Quitar novedad de Persona Ficticia Tres' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Quitar novedad' });
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    expect(calls.remove).toHaveLength(0);
    await waitFor(() => expect(row('Persona Ficticia Tres')).toHaveFocus());

    await user.click(
      screen.getByRole('button', { name: 'Quitar novedad de Persona Ficticia Tres' }),
    );
    await user.click(
      within(await screen.findByRole('dialog', { name: 'Quitar novedad' })).getByRole('button', {
        name: 'Quitar',
      }),
    );

    await waitFor(() => expect(calls.remove).toHaveLength(1));
    expect(calls.remove[0]).toEqual({
      respondentCode: '2000003',
      expectedNoveltyCode: 8,
      expectedOtherCause: 'Licencia “ficticia”',
    });
    await waitFor(() => expect(calls.peopleRequests).toHaveLength(2));
  });

  it('showsAConflictWhenRemovingSomethingThatChanged', async () => {
    serve();
    server.use(
      http.post('*/api/v1/admin/novelties/remove', () =>
        HttpResponse.json(
          { title: 'Conflicto', detail: 'Cambió.' },
          { status: 409, headers: PROBLEM },
        ),
      ),
    );
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Quitar novedad de Persona Ficticia Uno' }),
    );
    await user.click(
      within(await screen.findByRole('dialog', { name: 'Quitar novedad' })).getByRole('button', {
        name: 'Quitar',
      }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(/cambió mientras la editaba/);
  });

  it('showsAnErrorWhenRemovingFails', async () => {
    serve();
    server.use(
      http.post('*/api/v1/admin/novelties/remove', () =>
        HttpResponse.json(
          { title: 'No encontrado', detail: 'No se encontró el funcionario.' },
          { status: 404, headers: PROBLEM },
        ),
      ),
    );
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Quitar novedad de Persona Ficticia Uno' }),
    );
    await user.click(
      within(await screen.findByRole('dialog', { name: 'Quitar novedad' })).getByRole('button', {
        name: 'Quitar',
      }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('No se encontró el funcionario.');
  });

  it('returnsTheFocusToTheRowWhenTheEditorClosesBySavingOrCancelling', async () => {
    serve();
    const user = await openList();

    await user.click(
      screen.getByRole('button', { name: 'Asignar novedad de Persona Ficticia Dos' }),
    );
    await user.click(
      within(await screen.findByRole('dialog', { name: /Asignar novedad de/ })).getByRole(
        'button',
        {
          name: 'Cancelar',
        },
      ),
    );
    await waitFor(() => expect(row('Persona Ficticia Dos')).toHaveFocus());

    await user.click(
      screen.getByRole('button', { name: 'Asignar novedad de Persona Ficticia Dos' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /Asignar novedad de/ });
    await user.selectOptions(within(dialog).getByLabelText('Novedad'), '3');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }));
    await waitFor(() => expect(row('Persona Ficticia Dos')).toHaveFocus());
  });

  it('showsAMessageForAnEntityWithoutPeople', async () => {
    serve({ people: [] });
    await openList(false);

    expect(
      await screen.findByText('Esta entidad no tiene funcionarios en la muestra.'),
    ).toBeVisible();
  });

  it('showsAnErrorStateWhenThePeopleCannotBeLoaded', async () => {
    serve();
    server.use(
      http.post('*/api/v1/admin/novelties/people', () =>
        HttpResponse.json(
          { title: 'Prohibido', detail: 'Sin acceso.' },
          { status: 403, headers: PROBLEM },
        ),
      ),
    );
    renderApp(PAGE_URL);
    const user = userEvent.setup();
    await user.selectOptions(await screen.findByLabelText('Departamento'), '5');
    const entity = await screen.findByLabelText('Entidad');
    await waitFor(() => expect(within(entity).getAllByRole('option')).toHaveLength(2));
    await user.selectOptions(entity, '900001');

    expect(await screen.findByRole('alert')).toHaveTextContent('Sin acceso.');
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeVisible();
  });

  it('showsErrorsOfTheEntityAndCatalogLists', async () => {
    serve();
    server.use(
      http.get('*/api/v1/admin/novelties/entities', () =>
        HttpResponse.json(
          { title: 'Prohibido', detail: 'Sin acceso a entidades.' },
          { status: 403, headers: PROBLEM },
        ),
      ),
      http.get('*/api/v1/admin/novelties/catalog', () =>
        HttpResponse.json(
          { title: 'Error', detail: 'Catálogo no disponible.' },
          { status: 500, headers: PROBLEM },
        ),
      ),
    );
    renderApp(PAGE_URL);
    await userEvent.selectOptions(await screen.findByLabelText('Departamento'), '5');

    expect(await screen.findByText('Sin acceso a entidades.')).toBeVisible();
    expect(await screen.findByText('Catálogo no disponible.')).toBeVisible();
  });

  it('resetsTheEntityWhenTheDepartmentChanges', async () => {
    serve();
    const user = await openList();

    await user.selectOptions(screen.getByLabelText('Departamento'), '8');

    await waitFor(() =>
      expect(screen.queryByRole('table', { name: /Funcionarios/ })).not.toBeInTheDocument(),
    );
  });

  it('hasNoAccessibilityViolationsWithTheListAndTheEditor', async () => {
    serve();
    const user = await openList();
    expect((await axe.run(document.body, OPTIONS)).violations).toEqual([]);

    await user.click(
      screen.getByRole('button', { name: 'Cambiar novedad de Persona Ficticia Tres' }),
    );
    await screen.findByRole('dialog', { name: /Cambiar novedad de/ });
    expect((await axe.run(document.body, OPTIONS)).violations).toEqual([]);
  });
});
