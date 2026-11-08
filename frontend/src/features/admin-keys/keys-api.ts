import { apiClient, ensureCsrfToken } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';
import { downloadReportExcel } from '../admin-reports/reports-api';

export type KeysSummary = components['schemas']['KeysSummary'];
export type DepartmentKeys = components['schemas']['DepartmentKeys'];

// Las llaves llevan solo el año: las claves nunca pasan por la caché ni por la URL.
export const KEYS_QUERY_ROOT = ['admin', 'keys'] as const;

export function keysSummaryQueryKey(year: number | undefined) {
  return [...KEYS_QUERY_ROOT, 'summary', year ?? null] as const;
}

export function fetchKeysSummary(year: number | undefined): Promise<KeysSummary> {
  return unwrap(apiClient.GET('/api/v1/admin/keys/summary', { params: { query: { anio: year } } }));
}

/** Devuelve solo la cantidad: el servidor no entrega las claves, van únicamente en el Excel. */
export async function generateKeys(
  year: number | undefined,
  expectedWithoutKey: number,
): Promise<number> {
  await ensureCsrfToken();
  const result = await unwrap(
    apiClient.POST('/api/v1/admin/keys/generate', {
      body: { anio: year, expectedWithoutKey },
    }),
  );
  return result.generated ?? 0;
}

export function downloadKeysExcel(year: number | undefined, department: number): Promise<void> {
  return downloadReportExcel(
    '/api/v1/admin/keys/excel',
    { query: { anio: year, departamento: department } },
    `claves-${year ?? 'actual'}-dpto${department}.xlsx`,
  );
}
