import { READ_ONLY_NOTE } from '../../components/ReadOnlyNotice';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { readOnlyEnvironment, server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';
import type { PublicationChecklist } from './publish-api';

const PUBLISH_URL = '/admin/cuestionario/EDIN/2027/publicar';
const CHECKLIST_API = '*/api/v1/admin/questionnaire/EDIN/2027/publication';
const EXPORT_API = '*/api/v1/admin/questionnaire/EDIN/2027/rules/export';
const PROBLEM = { 'Content-Type': 'application/problem+json' };

function loginAsAdmin() {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({
        accountType: 'ADMINISTRATOR',
        roles: ['ADMIN_CUESTIONARIO'],
        departmentCode: '11',
      }),
    ),
    http.get('*/api/v1/auth/csrf', () => HttpResponse.json({ token: 'x' })),
  );
}

const READY: PublicationChecklist = {
  version: 7,
  ready: true,
  items: [
    { code: 'SIN_ERRORES', passed: true, detail: 'El análisis no tiene errores.', elements: [] },
    { code: 'CASOS_EN_VERDE', passed: true, detail: 'Los 2 casos de prueba pasan.', elements: [] },
    { code: 'COLUMNAS_CREADAS', passed: true, detail: 'Todas tienen su columna.', elements: [] },
    { code: 'TABLAS_VALIDAS', passed: true, detail: 'Todas las tablas son válidas.', elements: [] },
  ],
  warnings: [],
};

const BLOCKED: PublicationChecklist = {
  version: 7,
  ready: false,
  items: [
    {
      code: 'SIN_ERRORES',
      passed: false,
      detail: 'El análisis tiene 2 errores.',
      elements: ['regla:c01a-no', 'pregunta:C03'],
    },
    {
      code: 'CASOS_EN_VERDE',
      passed: false,
      detail: 'Hay casos que fallan o están por revisar.',
      elements: ['caso-b'],
    },
    {
      code: 'COLUMNAS_CREADAS',
      passed: false,
      detail: 'Falta 1 columna; descargue el script.',
      elements: ['C99'],
    },
    {
      code: 'TABLAS_VALIDAS',
      passed: false,
      detail: 'Hay tablas del mapeo que no existen o no son válidas.',
      elements: ['C2'],
    },
  ],
  warnings: ['SALTO_ATRAS@regla:c01a-no'],
};

function serve(checklist: PublicationChecklist, ruleSet: object = { estado: 'borrador' }) {
  loginAsAdmin();
  server.use(
    http.get(CHECKLIST_API, () => HttpResponse.json(checklist)),
    http.get(EXPORT_API, () => HttpResponse.json(ruleSet)),
  );
}

async function openPage() {
  const app = renderApp(PUBLISH_URL);
  await screen.findByRole('heading', { name: 'Publicar EDIN 2027' });
  await screen.findByRole('region', { name: 'Resumen de la lista de publicación' });
  return app;
}

describe('PublishPage', () => {
  it('muestra cada condición con ícono y texto, y enlaces a los elementos', async () => {
    serve(BLOCKED);
    await openPage();

    const list = screen.getByRole('list', { name: 'Condiciones para publicar' });
    const items = within(list).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('No cumple');
    expect(items[0]).toHaveTextContent('El análisis tiene 2 errores.');
    expect(screen.getByRole('link', { name: 'Ir a la regla c01a-no' })).toHaveAttribute(
      'href',
      '/admin/cuestionario/EDIN/2027/reglas#regla-c01a-no',
    );
    expect(screen.getByRole('link', { name: 'Ir a la pregunta C03' })).toHaveAttribute(
      'href',
      '/admin/cuestionario/EDIN/2027/reglas#pregunta-C03',
    );
    expect(screen.getByRole('link', { name: 'Ir al caso caso-b' })).toHaveAttribute(
      'href',
      '/admin/cuestionario/EDIN/2027/simulador#caso-caso-b',
    );
    expect(screen.getByRole('link', { name: 'Ir a la columna C99' })).toHaveAttribute(
      'href',
      '/admin/cuestionario/EDIN/2027',
    );
    expect(
      screen.getByRole('link', { name: 'Ir al panel de tablas del capítulo C2' }),
    ).toHaveAttribute('href', '/admin/cuestionario/EDIN/2027#titulo-tablas');
    expect(screen.getByText('SALTO_ATRAS@regla:c01a-no')).toBeInTheDocument();
  });

  it('muestra “Cumple” en las condiciones aprobadas', async () => {
    serve(READY);
    await openPage();

    const items = within(
      screen.getByRole('list', { name: 'Condiciones para publicar' }),
    ).getAllByRole('listitem');
    expect(items).toHaveLength(4);
    for (const item of items) {
      expect(item).toHaveTextContent('Cumple');
    }
  });

  it('deshabilita Publicar mientras la lista no esté completa', async () => {
    serve(BLOCKED);
    await openPage();

    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
  });

  it('confirma y publica con la versión de la lista', async () => {
    serve(READY);
    let body: unknown;
    server.use(
      http.post(CHECKLIST_API, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(READY);
      }),
    );
    await openPage();

    await userEvent.click(screen.getByRole('button', { name: 'Publicar' }));
    expect(
      await screen.findByText('Publicar bloquea la edición estructural del año 2027. ¿Continuar?'),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar publicación' }));

    await waitFor(() => expect(body).toEqual({ version: 7 }));
  });

  it('con un 422 vuelve a mostrar la lista devuelta y enfoca el resumen', async () => {
    serve(READY);
    server.use(
      http.post(CHECKLIST_API, () =>
        HttpResponse.json(
          {
            title: 'Lista de publicación incompleta',
            detail: 'El cuestionario no cumple las condiciones para publicarse.',
            status: 422,
            checklist: BLOCKED,
          },
          { status: 422, headers: PROBLEM },
        ),
      ),
    );
    await openPage();

    await userEvent.click(screen.getByRole('button', { name: 'Publicar' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar publicación' }));

    expect(await screen.findByText('El análisis tiene 2 errores.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
    await waitFor(() =>
      expect(
        screen.getByRole('region', { name: 'Resumen de la lista de publicación' }),
      ).toHaveFocus(),
    );
  });

  it('en un año publicado muestra quién, cuándo y la versión, y bloquea Publicar', async () => {
    serve(READY, {
      estado: 'publicado',
      publicacion: {
        fecha: '2027-03-05T15:00:00Z',
        usuario: 'admin.ficticio',
        version: 7,
        avisos: ['SALTO_ATRAS@regla:c01a-no'],
      },
    });
    await openPage();

    expect(
      await screen.findByText(/Publicado el .* por admin\.ficticio, versión 7/),
    ).toBeInTheDocument();
    expect(screen.getByText('SALTO_ATRAS@regla:c01a-no')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
  });
});

describe('PublishPage en un ambiente de solo lectura', () => {
  it('muestra la lista pero deshabilita Publicar y lo enlaza al aviso', async () => {
    readOnlyEnvironment();
    serve(READY);
    await openPage();

    expect(await screen.findByRole('note')).toHaveTextContent(READ_ONLY_NOTE);
    const publish = screen.getByRole('button', { name: 'Publicar' });
    expect(publish).toBeDisabled();
    expect(publish).toHaveAccessibleDescription(READ_ONLY_NOTE);
  });
});
