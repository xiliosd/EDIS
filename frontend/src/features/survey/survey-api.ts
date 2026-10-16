import { apiClient, readCookie } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';

export type SurveyProgress = components['schemas']['SurveyProgress'];
export type PageStatus = components['schemas']['PageStatus'];
export type PageDetail = components['schemas']['PageDetail'];
export type QuestionnaireEntry = components['schemas']['QuestionnaireEntry'];
export type PageSaveResult = components['schemas']['PageSaveResult'];
export type FinishResult = components['schemas']['FinishResult'];

export const PROGRESS_QUERY_KEY = ['survey', 'progress'] as const;
export const RULES_QUERY_KEY = ['survey', 'rules'] as const;

export function pageQueryKey(pageCode: string) {
  return ['survey', 'page', pageCode] as const;
}

export function fetchProgress() {
  return unwrap(apiClient.GET('/api/v1/survey/progress'));
}

export function fetchPage(pageCode: string) {
  return unwrap(
    apiClient.GET('/api/v1/survey/pages/{pageCode}', { params: { path: { pageCode } } }),
  );
}

export function fetchRules() {
  return unwrap(apiClient.GET('/api/v1/survey/rules'));
}

// La cookie XSRF-TOKEN puede faltar si el navegador la borró; se pide antes de escribir.
async function ensureCsrfToken(): Promise<void> {
  if (!readCookie('XSRF-TOKEN')) {
    await unwrap(apiClient.GET('/api/v1/auth/csrf'));
  }
}

export async function savePageAnswers(
  pageCode: string,
  answers: Record<string, string>,
): Promise<PageSaveResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/survey/pages/{pageCode}/answers', {
      params: { path: { pageCode } },
      body: { answers },
    }),
  );
}

export async function finishSurvey(observations: string): Promise<FinishResult> {
  await ensureCsrfToken();
  return unwrap(apiClient.POST('/api/v1/survey/finish', { body: { observations } }));
}
