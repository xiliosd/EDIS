import { http, HttpResponse } from 'msw';
import type {
  AdminEntry,
  AdminPage,
  AdminQuestionnaireView,
} from '../features/admin-questions/admin-api';
import { server } from './msw-server';

// Datos ficticios del árbol de un año en borrador: dos páginas, una con la tabla sin crear.
/**
 * Plazo de los flujos largos (abrir el árbol, editar varias veces, recargar). Solos tardan ~3 s, pero
 * con todos los archivos en paralelo en un equipo cargado superan los 5 s por defecto.
 */
export const LONG_FLOW_TIMEOUT = 15000;

export const TREE_URL = '/admin/cuestionario/EDIN/2027';
export const API_BASE = '*/api/v1/admin/questionnaire/EDIN/2027';

export function entry(overrides: Partial<AdminEntry>): AdminEntry {
  return {
    capProyId: 1,
    numeral: 'C01',
    columnCode: 'C01',
    kind: 'ITEM',
    inputType: 'SINGLE_CHOICE',
    text: 'Pregunta de prueba',
    rawText: 'Pregunta de prueba',
    columnStatus: 'ACTIVE',
    optionGroup: 1,
    headers: [],
    referencedByRules: [],
    ...overrides,
  };
}

export const PAGE_C1: AdminPage = {
  pageCode: 'C1',
  chapterCode: 'C',
  tableName: 'EDIN_27_CAP_C',
  tableMissing: false,
  entries: [
    entry({
      capProyId: 101,
      numeral: 'C05',
      columnCode: 'C05',
      text: '¿En 2027 trabajó en la entidad?',
      rawText: '¿En 200X trabajó en la entidad?',
      headers: [{ codEnc: 501, kind: 'HEADING', text: 'Ambiente laboral' }],
      help: { codAyu: 701, text: 'Responda según el último año.' },
      referencedByRules: ['salto-c05'],
    }),
    entry({ capProyId: 102, numeral: 'C06', columnCode: 'C06', text: 'Segunda pregunta' }),
    entry({
      capProyId: 103,
      numeral: 'C07A',
      columnCode: 'C07A',
      inputType: 'TEXT',
      optionGroup: undefined,
      text: 'Tercera pregunta',
      rawText: 'Tercera pregunta',
      columnStatus: 'PENDING_COLUMN',
    }),
  ],
};

export const PAGE_H1: AdminPage = {
  pageCode: 'H1',
  chapterCode: 'H',
  tableName: 'EDIN_27_CAP_H',
  tableMissing: true,
  entries: [
    entry({ capProyId: 201, numeral: 'H01', columnCode: 'H01', optionGroup: 7, text: 'Otra' }),
  ],
};

export function draftView(overrides: Partial<AdminQuestionnaireView> = {}): AdminQuestionnaireView {
  return {
    survey: 'EDIN',
    year: 2027,
    state: 'borrador',
    version: 4,
    tables: { C: 'EDIN_27_CAP_C', H: 'EDIN_27_CAP_H' },
    pages: [PAGE_C1, PAGE_H1],
    ...overrides,
  };
}

export function problem(status: number, detail: string, errors?: Record<string, unknown>) {
  return HttpResponse.json(
    { title: 'Error', detail, ...(errors ? { errors } : {}) },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

/** Sesión con el rol del editor y el árbol indicado en GET. */
export function serveTree(view: AdminQuestionnaireView = draftView()) {
  document.cookie = 'XSRF-TOKEN=token-de-prueba';
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({
        accountType: 'ADMINISTRATOR',
        roles: ['ADMIN_CUESTIONARIO'],
        departmentCode: '11',
      }),
    ),
    http.get(API_BASE, () => HttpResponse.json(view)),
  );
}
