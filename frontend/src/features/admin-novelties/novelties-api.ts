import { apiClient, ensureCsrfToken } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';

export type NoveltyCatalogEntry = components['schemas']['NoveltyCatalogEntry'];
export type NoveltyEntity = components['schemas']['NoveltyEntity'];
export type NoveltyPerson = components['schemas']['NoveltyPerson'];

/** Máximo de bytes Latin-9 de "Otra causa" en la base (igual que el servidor). */
export const OTHER_CAUSE_MAX_BYTES = 500;

// Las llaves llevan solo año, departamento y entidad: la cédula nunca forma parte de una llave.
export const NOVELTIES_QUERY_ROOT = ['admin', 'novelties'] as const;

export function noveltyCatalogQueryKey(year: number | undefined) {
  return [...NOVELTIES_QUERY_ROOT, 'catalog', year ?? null] as const;
}

export function noveltyEntitiesQueryKey(year: number | undefined, department: number | undefined) {
  return [...NOVELTIES_QUERY_ROOT, 'entities', year ?? null, department ?? null] as const;
}

export function noveltyPeopleQueryKey(
  year: number | undefined,
  department: number | undefined,
  entity: number | undefined,
) {
  return [
    ...NOVELTIES_QUERY_ROOT,
    'people',
    year ?? null,
    department ?? null,
    entity ?? null,
  ] as const;
}

export function fetchNoveltyCatalog(year: number | undefined): Promise<NoveltyCatalogEntry[]> {
  return unwrap(
    apiClient.GET('/api/v1/admin/novelties/catalog', { params: { query: { anio: year } } }),
  );
}

export function fetchNoveltyEntities(
  year: number | undefined,
  department: number,
): Promise<NoveltyEntity[]> {
  return unwrap(
    apiClient.GET('/api/v1/admin/novelties/entities', {
      params: { query: { anio: year, departamento: department } },
    }),
  );
}

/** Es un POST (no GET) para que ninguna cédula ni dato del listado viaje en la URL. */
export async function fetchNoveltyPeople(
  year: number | undefined,
  department: number,
  entity: number,
): Promise<NoveltyPerson[]> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.POST('/api/v1/admin/novelties/people', {
      body: { anio: year, departamento: department, entidad: entity },
    }),
  );
}

/** Valores de la novedad tal como se leyeron en pantalla: el servidor detecta cambios ajenos. */
export interface NoveltyExpected {
  expectedNoveltyCode?: number;
  expectedOtherCause?: string;
}

export interface NoveltyAssignment extends NoveltyExpected {
  year: number | undefined;
  respondentCode: string;
  noveltyCode: number;
  otherCause?: string;
}

export async function assignNovelty(assignment: NoveltyAssignment): Promise<void> {
  await ensureCsrfToken();
  await unwrap(
    apiClient.POST('/api/v1/admin/novelties/assign', {
      body: {
        anio: assignment.year,
        respondentCode: assignment.respondentCode,
        noveltyCode: assignment.noveltyCode,
        otherCause: assignment.otherCause,
        expectedNoveltyCode: assignment.expectedNoveltyCode,
        expectedOtherCause: assignment.expectedOtherCause,
      },
    }),
  );
}

export async function removeNovelty(
  year: number | undefined,
  respondentCode: string,
  expected: NoveltyExpected,
): Promise<void> {
  await ensureCsrfToken();
  await unwrap(
    apiClient.POST('/api/v1/admin/novelties/remove', {
      body: { anio: year, respondentCode, ...expected },
    }),
  );
}
