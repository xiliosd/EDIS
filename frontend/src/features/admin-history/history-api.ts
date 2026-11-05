import { apiClient, readCookie } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';
import type { RuleSet } from '../admin-rules/rules-api';

export type HistoryView = components['schemas']['HistoryView'];
export type HistoryEntry = components['schemas']['HistoryEntry'];
export type RulesEditResult = components['schemas']['RulesEditResult'];

export function historyQueryKey(survey: string, year: number) {
  return ['admin', 'history', survey, year] as const;
}

export function historyVersionQueryKey(survey: string, year: number, version: number) {
  return [...historyQueryKey(survey, year), 'version', version] as const;
}

// La cookie XSRF-TOKEN puede faltar si el navegador la borró; se pide antes de escribir.
async function ensureCsrfToken(): Promise<void> {
  if (!readCookie('XSRF-TOKEN')) {
    await unwrap(apiClient.GET('/api/v1/auth/csrf'));
  }
}

export function fetchHistory(survey: string, year: number): Promise<HistoryView> {
  return unwrap(
    apiClient.GET('/api/v1/admin/questionnaire/{encuesta}/{anio}/history', {
      params: { path: { encuesta: survey, anio: year } },
    }),
  );
}

export function fetchHistoryVersion(
  survey: string,
  year: number,
  version: number,
): Promise<RuleSet> {
  return unwrap(
    apiClient.GET('/api/v1/admin/questionnaire/{encuesta}/{anio}/history/{version}', {
      params: { path: { encuesta: survey, anio: year, version } },
    }),
  );
}

/** Restaura una versión; `currentVersion` es la vigente que se leyó (control de concurrencia). */
export async function restoreVersion(
  survey: string,
  year: number,
  version: number,
  currentVersion: number,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.POST('/api/v1/admin/questionnaire/{encuesta}/{anio}/history/{version}/restore', {
      params: { path: { encuesta: survey, anio: year, version } },
      body: { version: currentVersion },
    }),
  );
}
