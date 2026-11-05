import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';
import * as downloadFile from '../../services/download-file';

const PAGE_URL = '/admin/cuestionario/EDIN/2027/paquetes';
const BASE_API = '*/api/v1/admin/questionnaire/EDIN/2027';
const PROBLEM = { 'Content-Type': 'application/problem+json' };
const FILE_LABEL = 'Paquete de promoción (.zip, máximo 5 MB)';

afterEach(() => vi.restoreAllMocks());

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

function serve(options: { published?: boolean; laterVersion?: boolean } = {}) {
  const { published = true, laterVersion = true } = options;
  loginAsAdmin();
  server.use(
    http.get(`${BASE_API}/history`, () =>
      HttpResponse.json({
        version: laterVersion ? 5 : 4,
        state: published ? 'publicado' : 'borrador',
        entries: [],
      }),
    ),
    http.get(`${BASE_API}/rules/export`, () =>
      HttpResponse.text(
        JSON.stringify({
          estado: published ? 'publicado' : 'borrador',
          version: laterVersion ? 5 : 4,
          publicacion: { version: 4, usuario: 'ana.prueba', fecha: '2026-11-01T10:00:00Z' },
        }),
      ),
    ),
  );
}

async function openPage() {
  const app = renderApp(PAGE_URL);
  await screen.findByRole('heading', { name: 'Paquetes EDIN 2027' });
  await screen.findByRole('button', { name: 'Descargar paquete del año' });
  return app;
}

function zipFile(name = 'paquete.zip', size = 10) {
  const file = new File(['x'], name, { type: 'application/zip' });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

const PREVIEW = {
  kind: 'correccion',
  version: 5,
  alreadyImported: false,
  overwritesLocalChange: false,
  impact: [
    { pageCode: 'P03', savedCount: 12 },
    { pageCode: 'P04', savedCount: 0 },
  ],
};

async function verifyPackage() {
  await userEvent.upload(screen.getByLabelText(FILE_LABEL), zipFile());
  await userEvent.click(screen.getByRole('button', { name: 'Verificar' }));
}

describe('PackagesPage', () => {
  it('avisa que primero se ejecutan columnas.sql y frm.sql', async () => {
    serve();
    await openPage();

    expect(
      screen.getByText('Ejecute primero columnas.sql y frm.sql con su usuario de base de datos.'),
    ).toBeInTheDocument();
  });

  it('descarga el paquete del año', async () => {
    serve();
    const download = vi.spyOn(downloadFile, 'downloadBlobFile').mockImplementation(() => {});
    let tipo = '';
    server.use(
      http.get(`${BASE_API}/package`, ({ request }) => {
        tipo = new URL(request.url).searchParams.get('tipo') ?? '';
        return new HttpResponse('zip', { headers: { 'Content-Type': 'application/zip' } });
      }),
    );
    await openPage();

    await userEvent.click(screen.getByRole('button', { name: 'Descargar paquete del año' }));

    await waitFor(() => expect(download).toHaveBeenCalled());
    expect(tipo).toBe('anio');
    expect(download.mock.calls[0][0]).toBe('paquete-EDIN-2027-anio.zip');
  });

  it('deshabilita el paquete de corrección si no hay versión posterior a la publicación', async () => {
    serve({ laterVersion: false });
    await openPage();

    expect(screen.getByRole('button', { name: 'Descargar paquete de corrección' })).toBeDisabled();
    expect(
      screen.getByText('No hay correcciones posteriores a la publicación.'),
    ).toBeInTheDocument();
  });

  it('habilita el paquete de corrección con una versión posterior', async () => {
    serve();
    await openPage();

    expect(screen.getByRole('button', { name: 'Descargar paquete de corrección' })).toBeEnabled();
  });

  it('muestra la lista de publicación cuando el servidor no genera el paquete (422)', async () => {
    serve();
    server.use(
      http.get(`${BASE_API}/package`, () =>
        HttpResponse.json(
          {
            title: 'Lista de publicación incompleta',
            detail: 'El año ya no pasa la lista de publicación.',
            checklist: {
              version: 5,
              ready: false,
              items: [{ code: 'SIN_ERRORES', passed: false, detail: 'El análisis tiene 1 error.' }],
            },
          },
          { status: 422, headers: PROBLEM },
        ),
      ),
    );
    await openPage();

    await userEvent.click(screen.getByRole('button', { name: 'Descargar paquete del año' }));

    const summary = await screen.findByRole('region', {
      name: 'Resumen de la lista de publicación',
    });
    expect(summary).toHaveTextContent('El análisis tiene 1 error.');
    await waitFor(() => expect(summary).toHaveFocus());
  });

  it('rechaza en el cliente un archivo de más de 5 MB sin llamar al servidor', async () => {
    serve();
    let called = false;
    server.use(
      http.post(`${BASE_API}/package/import`, () => {
        called = true;
        return HttpResponse.json(PREVIEW);
      }),
    );
    await openPage();

    await userEvent.upload(
      screen.getByLabelText(FILE_LABEL),
      zipFile('grande.zip', 5 * 1024 * 1024 + 1),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('El paquete supera 5 MB.');
    expect(screen.getByRole('button', { name: 'Verificar' })).toBeDisabled();
    expect(called).toBe(false);
  });

  it('exige verificar antes de importar', async () => {
    serve();
    await openPage();

    expect(screen.getByRole('button', { name: 'Importar' })).toBeDisabled();
    await userEvent.upload(screen.getByLabelText(FILE_LABEL), zipFile());
    expect(screen.getByRole('button', { name: 'Importar' })).toBeDisabled();
  });

  it('muestra el impacto con la frase exacta y mueve el foco al resultado', async () => {
    serve();
    let confirmar: string | null = '';
    server.use(
      http.post(`${BASE_API}/package/import`, ({ request }) => {
        confirmar = new URL(request.url).searchParams.get('confirmar');
        return HttpResponse.json(PREVIEW);
      }),
    );
    await openPage();

    await verifyPackage();

    const result = await screen.findByRole('region', { name: 'Resultado de la verificación' });
    expect(confirmar).toBe('false');
    const table = within(result).getByRole('table', { name: 'Impacto por página' });
    expect(within(table).getByText('P03')).toBeInTheDocument();
    expect(
      within(table).getByText('12 encuestados ya guardaron esta página con la regla anterior.'),
    ).toBeInTheDocument();
    await waitFor(() => expect(result).toHaveFocus());
    expect(screen.getByRole('button', { name: 'Importar' })).toBeEnabled();
  });

  it('avisa si el paquete ya estaba importado o reemplaza un cambio local', async () => {
    serve();
    server.use(
      http.post(`${BASE_API}/package/import`, () =>
        HttpResponse.json({ ...PREVIEW, alreadyImported: true, overwritesLocalChange: true }),
      ),
    );
    await openPage();

    await verifyPackage();

    expect(await screen.findByText('El paquete ya estaba importado.')).toBeInTheDocument();
    expect(screen.getByText(/reemplaza un cambio hecho en este ambiente/)).toBeInTheDocument();
  });

  it('lista las diferencias cuando la revisión devuelve 422', async () => {
    serve();
    server.use(
      http.post(`${BASE_API}/package/import`, () =>
        HttpResponse.json(
          {
            title: 'El paquete no pasa la revisión',
            detail: 'Los archivos del paquete no coinciden con el manifiesto.',
            differences: ['reglas.json', 'C30A: falta la columna.'],
          },
          { status: 422, headers: PROBLEM },
        ),
      ),
    );
    await openPage();

    await verifyPackage();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Los archivos del paquete no coinciden con el manifiesto.');
    const list = within(alert).getByRole('list', { name: 'Diferencias encontradas' });
    expect(
      within(list)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['reglas.json', 'C30A: falta la columna.']);
    await waitFor(() => expect(alert).toHaveFocus());
    expect(screen.getByRole('button', { name: 'Importar' })).toBeDisabled();
  });

  it('importa con confirmar=true tras confirmar en el diálogo', async () => {
    serve();
    const calls: (string | null)[] = [];
    server.use(
      http.post(`${BASE_API}/package/import`, ({ request }) => {
        calls.push(new URL(request.url).searchParams.get('confirmar'));
        return HttpResponse.json(PREVIEW);
      }),
    );
    await openPage();
    await verifyPackage();
    await screen.findByRole('region', { name: 'Resultado de la verificación' });

    await userEvent.click(screen.getByRole('button', { name: 'Importar' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar importación' }));

    await waitFor(() => expect(calls).toEqual(['false', 'true']));
    expect(await screen.findByText('El paquete se importó.')).toBeInTheDocument();
  });

  it('muestra el error del servidor para un paquete anterior (409)', async () => {
    serve();
    server.use(
      http.post(`${BASE_API}/package/import`, () =>
        HttpResponse.json(
          {
            title: 'Paquete anterior',
            detail: 'El paquete es anterior a la versión de este ambiente.',
          },
          { status: 409, headers: PROBLEM },
        ),
      ),
    );
    await openPage();

    await verifyPackage();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El paquete es anterior a la versión de este ambiente.',
    );
  });
});
