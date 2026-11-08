import { apiClient, ensureCsrfToken } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';
import { downloadBlobFile, serverFileName } from '../../services/download-file';

export type ReportSurvey = NonNullable<components['schemas']['CoverageReport']['survey']>;
export type CoverageReport = components['schemas']['CoverageReport'];
export type CoverageTotals = components['schemas']['CoverageTotals'];
export type DepartmentCoverage = components['schemas']['DepartmentCoverage'];
export type EntityCoverage = components['schemas']['EntityCoverage'];
export type LevelCoverage = components['schemas']['LevelCoverage'];
export type CutSummary = components['schemas']['CutSummary'];

export const REPORT_SURVEYS: readonly { value: ReportSurvey; label: string }[] = [
  { value: 'GENERAL', label: 'General' },
  { value: 'EDI', label: 'EDI' },
  { value: 'EDID', label: 'EDID' },
];

/** Filtros comunes de los reportes; sin año ni departamento, el servidor usa sus valores por defecto. */
export interface ReportFilterValue {
  survey: ReportSurvey;
  year?: number;
  department?: number;
}

export const REPORT_YEARS_QUERY_KEY = ['admin', 'reports', 'years'] as const;

export function coverageQueryKey(filters: ReportFilterValue) {
  return ['admin', 'reports', 'coverage', filters] as const;
}

export function cutsQueryKey(year: number | undefined, department: number | undefined) {
  return ['admin', 'reports', 'cuts', year ?? null, department ?? null] as const;
}

export function fetchReportYears(): Promise<number[]> {
  return unwrap(apiClient.GET('/api/v1/admin/reports/years'));
}

function coverageQuery(filters: ReportFilterValue) {
  return { encuesta: filters.survey, anio: filters.year, departamento: filters.department };
}

export function fetchCoverage(filters: ReportFilterValue): Promise<CoverageReport> {
  return unwrap(
    apiClient.GET('/api/v1/admin/reports/coverage', { params: { query: coverageQuery(filters) } }),
  );
}

export function fetchCuts(
  year: number | undefined,
  department: number | undefined,
): Promise<CutSummary[]> {
  return unwrap(
    apiClient.GET('/api/v1/admin/reports/cuts', {
      params: { query: { anio: year, departamento: department } },
    }),
  );
}

export async function createCut(
  survey: ReportSurvey,
  department: number,
  year: number | undefined,
): Promise<CutSummary> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.POST('/api/v1/admin/reports/cuts', {
      body: { encuesta: survey, departamento: department, anio: year },
    }),
  );
}

/** Rutas de descarga .xlsx del API de reportes. */
export type ReportExcelPath =
  | '/api/v1/admin/reports/coverage/excel'
  | '/api/v1/admin/reports/cuts/{id}/excel'
  | '/api/v1/admin/reports/novelties/excel'
  | '/api/v1/admin/reports/operational-status/excel'
  | '/api/v1/admin/reports/performance/excel';

/** Parámetros de la petición de un Excel: filtros en `query` o el identificador en `path`. */
export interface ReportExcelParams {
  query?: Record<string, string | number | boolean | undefined>;
  path?: Record<string, string | number>;
}

/**
 * Descarga un reporte .xlsx con el nombre que fija el servidor (o `fallbackName`). Si el servidor
 * lo rechaza lanza ApiError sin descargar nada.
 */
export async function downloadReportExcel(
  path: ReportExcelPath,
  params: ReportExcelParams,
  fallbackName: string,
): Promise<void> {
  let disposition: string | null = null;
  // El tipo de `params` depende de cada ruta; aquí se acepta la forma común {query, path}.
  const request = apiClient
    .GET(path as '/api/v1/admin/reports/coverage/excel', {
      params: params as never,
      parseAs: 'blob',
    })
    .then((result) => {
      disposition = result.response.headers.get('Content-Disposition');
      return result;
    });
  const blob = (await unwrap(request)) as unknown as Blob;
  downloadBlobFile(serverFileName(disposition, '.xlsx') ?? fallbackName, blob);
}

export function downloadCoverageExcel(filters: ReportFilterValue): Promise<void> {
  const year = filters.year ?? 'actual';
  return downloadReportExcel(
    '/api/v1/admin/reports/coverage/excel',
    { query: coverageQuery(filters) },
    `cobertura-${filters.survey}-${year}.xlsx`,
  );
}

export function downloadCutExcel(id: string): Promise<void> {
  return downloadReportExcel(
    '/api/v1/admin/reports/cuts/{id}/excel',
    { path: { id } },
    `corte-cobertura-${id}.xlsx`,
  );
}
