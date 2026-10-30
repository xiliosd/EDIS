import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../../services/api-error';
import { server } from '../../test/msw-server';
import {
  addQuestion,
  deactivate,
  editHeader,
  editHelp,
  editText,
  fetchColumnScript,
  fetchCreateTableScript,
  fetchQuestionnaire,
  reorder,
  saveTables,
} from './admin-api';

const BASE = '*/api/v1/admin/questionnaire/EDIN/2027';
const EDIT_RESULT = { version: 4, page: { pageCode: 'C1', entries: [] } };

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token-de-prueba';
});

describe('admin-api', () => {
  it('descarga los scripts como texto plano, con los parámetros opcionales', async () => {
    let query = '';
    server.use(
      http.get(`${BASE}/entries/7/column-script`, ({ request }) => {
        query = new URL(request.url).search;
        return new HttpResponse('ALTER TABLE T ADD (C1 NUMBER(1));', {
          headers: { 'Content-Type': 'text/plain' },
        });
      }),
      http.get(`${BASE}/tables/C/create-script`, () => HttpResponse.text('CREATE TABLE T2 ...;')),
    );

    await expect(fetchColumnScript('EDIN', 2027, 7, 'NUMBER', 1)).resolves.toBe(
      'ALTER TABLE T ADD (C1 NUMBER(1));',
    );
    expect(query).toBe('?columnType=NUMBER&columnLength=1');
    await expect(fetchCreateTableScript('EDIN', 2027, 'C')).resolves.toBe('CREATE TABLE T2 ...;');
  });

  it('envía la versión leída y el token CSRF en cada edición', async () => {
    const seen: Record<string, unknown> = {};
    const record =
      (name: string) =>
      async ({ request }: { request: Request }) => {
        seen[name] = { csrf: request.headers.get('X-XSRF-TOKEN'), body: await bodyOf(request) };
        return HttpResponse.json(EDIT_RESULT);
      };
    server.use(
      http.put(`${BASE}/entries/7/text`, record('text')),
      http.put(`${BASE}/headers/3`, record('header')),
      http.put(`${BASE}/help/5`, record('help')),
      http.patch(`${BASE}/pages/C1/order`, record('order')),
      http.post(`${BASE}/pages/C1/entries`, record('add')),
      http.delete(`${BASE}/entries/7`, ({ request }) => {
        seen.deactivate = new URL(request.url).searchParams.get('version');
        return HttpResponse.json(EDIT_RESULT);
      }),
      http.put(`${BASE}/tables`, record('tables')),
    );

    await editText('EDIN', 2027, 7, 'Texto', 3);
    await editHeader('EDIN', 2027, 3, 'Encabezado', 3);
    await editHelp('EDIN', 2027, 5, 'Ayuda', 3);
    await reorder('EDIN', 2027, 'C1', [2, 1], 3);
    await addQuestion('EDIN', 2027, 'C1', {
      columnCode: 'P1',
      inputType: 'TEXT',
      numeral: '1',
      text: 'Nueva',
      version: 3,
    });
    await deactivate('EDIN', 2027, 7, 3);
    await saveTables('EDIN', 2027, { C: 'TABLA_C' }, 3);

    expect(seen.text).toEqual({ csrf: 'token-de-prueba', body: { text: 'Texto', version: 3 } });
    expect(seen.order).toMatchObject({ body: { capProyIds: [2, 1], version: 3 } });
    expect(seen.deactivate).toBe('3');
    expect(seen.tables).toMatchObject({ body: { tables: { C: 'TABLA_C' }, version: 3 } });
  });

  it('convierte las respuestas de error en ApiError', async () => {
    server.use(
      http.get(BASE, () =>
        HttpResponse.json(
          { title: 'No encontrado', detail: 'El año no existe.' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    await expect(fetchQuestionnaire('EDIN', 2027)).rejects.toMatchObject({
      status: 404,
      detail: 'El año no existe.',
    });
    await expect(fetchQuestionnaire('EDIN', 2027)).rejects.toBeInstanceOf(ApiError);
  });
});

async function bodyOf(request: Request) {
  return request.json();
}
