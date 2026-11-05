import { apiClient, ensureCsrfToken } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';
import { fetchRulesExport, type RuleSet } from '../admin-rules/rules-api';

export type PublicationChecklist = components['schemas']['PublicationChecklist'];
export type ChecklistItem = components['schemas']['ChecklistItem'];
export type Publication = components['schemas']['Publication'];

export function checklistQueryKey(survey: string, year: number) {
  return ['admin', 'publication', survey, year] as const;
}

export function ruleSetQueryKey(survey: string, year: number) {
  return ['admin', 'publication', survey, year, 'rule-set'] as const;
}

export function fetchChecklist(survey: string, year: number): Promise<PublicationChecklist> {
  return unwrap(
    apiClient.GET('/api/v1/admin/questionnaire/{encuesta}/{anio}/publication', {
      params: { path: { encuesta: survey, anio: year } },
    }),
  );
}

/** Estado y datos de publicación del año, leídos del archivo de reglas que guarda el servidor. */
export async function fetchRuleSet(survey: string, year: number): Promise<RuleSet> {
  return JSON.parse(await fetchRulesExport(survey, year)) as RuleSet;
}

export async function publishYear(
  survey: string,
  year: number,
  version: number,
): Promise<PublicationChecklist> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.POST('/api/v1/admin/questionnaire/{encuesta}/{anio}/publication', {
      params: { path: { encuesta: survey, anio: year } },
      body: { version },
    }),
  );
}
