import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../../services/api-error';
import { server } from '../../test/msw-server';
import {
  deleteCase,
  deleteSkip,
  deleteValidation,
  downloadRules,
  fetchRules,
  fetchSimulator,
  importRules,
  rulesQueryKey,
  saveCase,
  saveSkip,
  saveValidation,
  simulatorQueryKey,
} from './rules-api';

const BASE = '*/api/v1/admin/questionnaire/EDIN/2027';
const EDIT_RESULT = { version: 5, report: { errors: [], warnings: [] }, caseResults: [] };
const RULE = {
  id: 'e03-si',
  si: { pregunta: 'E03', op: 'eq', valor: '1' },
  entonces: { accion: 'deshabilitar', preguntas: ['E04'] },
};

// jsdom no implementa Blob.text().
function readBlob(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token-de-prueba';
});

describe('rules-api', () => {
  it('define claves de consulta estables', () => {
    expect(rulesQueryKey('EDIN', 2027)).toEqual(['admin', 'rules', 'EDIN', 2027]);
    expect(simulatorQueryKey('EDIN', 2027)).toEqual(['admin', 'simulator', 'EDIN', 2027]);
  });

  it('lee las reglas y el simulador', async () => {
    server.use(
      http.get(`${BASE}/rules`, () => HttpResponse.json({ version: 3, skips: [] })),
      http.get(`${BASE}/simulator`, () => HttpResponse.json({ version: 3, pages: [] })),
    );

    await expect(fetchRules('EDIN', 2027)).resolves.toMatchObject({ version: 3 });
    await expect(fetchSimulator('EDIN', 2027)).resolves.toMatchObject({ version: 3 });
  });

  it('envía versión y token CSRF en cada escritura', async () => {
    const seen: Record<string, unknown> = {};
    const record =
      (name: string) =>
      async ({ request }: { request: Request }) => {
        const text = await request.text();
        seen[name] = {
          csrf: request.headers.get('X-XSRF-TOKEN'),
          body: text ? JSON.parse(text) : null,
        };
        return HttpResponse.json(EDIT_RESULT);
      };
    const recordDelete =
      (name: string) =>
      ({ request }: { request: Request }) => {
        seen[name] = new URL(request.url).searchParams.get('version');
        return HttpResponse.json(EDIT_RESULT);
      };
    server.use(
      http.put(`${BASE}/rules/skips/e03-si`, record('skip')),
      http.put(`${BASE}/rules/validations/E03`, record('validation')),
      http.put(`${BASE}/rules/cases/caso-1`, record('case')),
      http.put(`${BASE}/rules/import`, record('import')),
      http.delete(`${BASE}/rules/skips/e03-si`, recordDelete('deleteSkip')),
      http.delete(`${BASE}/rules/validations/E03`, recordDelete('deleteValidation')),
      http.delete(`${BASE}/rules/cases/caso-1`, recordDelete('deleteCase')),
    );
    const testCase = { id: 'caso-1', nombre: 'Caso', pagina: 'C1', respuestas: {}, esperado: {} };

    await expect(saveSkip('EDIN', 2027, RULE, 4)).resolves.toMatchObject({ version: 5 });
    await saveValidation('EDIN', 2027, 'E03', { tipo: 'entero' }, 4);
    await saveCase('EDIN', 2027, testCase, 4);
    await importRules('EDIN', 2027, { encuesta: 'EDIN', anio: 2027 }, 4);
    await deleteSkip('EDIN', 2027, 'e03-si', 4);
    await deleteValidation('EDIN', 2027, 'E03', 4);
    await deleteCase('EDIN', 2027, 'caso-1', 4);

    expect(seen).toEqual({
      skip: { csrf: 'token-de-prueba', body: { rule: RULE, version: 4 } },
      validation: { csrf: 'token-de-prueba', body: { validation: { tipo: 'entero' }, version: 4 } },
      case: { csrf: 'token-de-prueba', body: { testCase, version: 4 } },
      import: {
        csrf: 'token-de-prueba',
        body: { rules: { encuesta: 'EDIN', anio: 2027 }, version: 4 },
      },
      deleteSkip: '4',
      deleteValidation: '4',
      deleteCase: '4',
    });
  });

  it('un 422 rechaza con ApiError que lleva el informe', async () => {
    const report = {
      errors: [{ code: 'CICLO', element: 'a', questions: ['E03'], message: 'Hay un ciclo.' }],
      warnings: [],
    };
    server.use(
      http.put(`${BASE}/rules/skips/e03-si`, () =>
        HttpResponse.json(
          { title: 'Reglas inválidas', detail: 'Las reglas agregan errores.', report },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const error = await saveSkip('EDIN', 2027, RULE, 4).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(422);
    expect((error as ApiError).report).toEqual(report);
    expect((error as ApiError).fieldErrors).toEqual({});
  });

  it('un 413 al importar conserva el detalle', async () => {
    server.use(
      http.put(`${BASE}/rules/import`, () =>
        HttpResponse.json(
          { title: 'Archivo muy grande', detail: 'El archivo supera 1 MB.' },
          { status: 413, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    await expect(importRules('EDIN', 2027, {}, 1)).rejects.toMatchObject({
      status: 413,
      detail: 'El archivo supera 1 MB.',
    });
  });

  it('descarga el archivo de reglas con el nombre ENCUESTA-AAAA.json', async () => {
    server.use(
      http.get(`${BASE}/rules/export`, () =>
        HttpResponse.json({ encuesta: 'EDIN', anio: 2027, version: 2 }),
      ),
    );
    const created: Blob[] = [];
    const names: string[] = [];
    URL.createObjectURL = (blob: Blob | MediaSource) => {
      created.push(blob as Blob);
      return 'blob:prueba';
    };
    URL.revokeObjectURL = () => undefined;
    const originalClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function click(this: HTMLAnchorElement) {
      names.push(this.download);
    };

    try {
      await downloadRules('EDIN', 2027);
    } finally {
      HTMLAnchorElement.prototype.click = originalClick;
    }

    expect(names).toEqual(['EDIN-2027.json']);
    expect(JSON.parse(await readBlob(created[0]))).toMatchObject({ encuesta: 'EDIN', anio: 2027 });
  });
});
