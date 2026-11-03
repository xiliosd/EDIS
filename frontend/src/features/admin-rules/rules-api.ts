import { apiClient, readCookie } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';
import { downloadTextFile } from '../../services/download-file';

export type RulesEditorView = components['schemas']['RulesEditorView'];
export type RulesEditResult = components['schemas']['RulesEditResult'];
export type AnalysisReport = components['schemas']['AnalysisReport'];
export type Finding = components['schemas']['Finding'];
export type CaseResult = components['schemas']['CaseResult'];
export type RuleQuestion = components['schemas']['RuleQuestion'];
export type SimulatorView = components['schemas']['SimulatorView'];
export type SkipRule = components['schemas']['SkipRule'];
export type Condition = components['schemas']['Condition'];
export type FieldValidation = components['schemas']['FieldValidation'];
export type TestCase = components['schemas']['TestCase'];
export type ExpectedOutcome = components['schemas']['ExpectedOutcome'];
export type RuleSet = components['schemas']['RuleSet'];

export function rulesQueryKey(survey: string, year: number) {
  return ['admin', 'rules', survey, year] as const;
}

export function simulatorQueryKey(survey: string, year: number) {
  return ['admin', 'simulator', survey, year] as const;
}

// La cookie XSRF-TOKEN puede faltar si el navegador la borró; se pide antes de escribir.
async function ensureCsrfToken(): Promise<void> {
  if (!readCookie('XSRF-TOKEN')) {
    await unwrap(apiClient.GET('/api/v1/auth/csrf'));
  }
}

export function fetchRules(survey: string, year: number): Promise<RulesEditorView> {
  return unwrap(
    apiClient.GET('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules', {
      params: { path: { encuesta: survey, anio: year } },
    }),
  );
}

export function fetchSimulator(survey: string, year: number): Promise<SimulatorView> {
  return unwrap(
    apiClient.GET('/api/v1/admin/questionnaire/{encuesta}/{anio}/simulator', {
      params: { path: { encuesta: survey, anio: year } },
    }),
  );
}

export async function saveSkip(
  survey: string,
  year: number,
  rule: SkipRule,
  version: number,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/skips/{ruleId}', {
      params: { path: { encuesta: survey, anio: year, ruleId: rule.id ?? '' } },
      body: { rule, version },
    }),
  );
}

export async function deleteSkip(
  survey: string,
  year: number,
  ruleId: string,
  version: number,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.DELETE('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/skips/{ruleId}', {
      params: { path: { encuesta: survey, anio: year, ruleId }, query: { version } },
    }),
  );
}

export async function saveValidation(
  survey: string,
  year: number,
  column: string,
  validation: FieldValidation,
  version: number,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/validations/{column}', {
      params: { path: { encuesta: survey, anio: year, column } },
      body: { validation, version },
    }),
  );
}

export async function deleteValidation(
  survey: string,
  year: number,
  column: string,
  version: number,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.DELETE('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/validations/{column}', {
      params: { path: { encuesta: survey, anio: year, column }, query: { version } },
    }),
  );
}

export async function saveCase(
  survey: string,
  year: number,
  testCase: TestCase,
  version: number,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/cases/{caseId}', {
      params: { path: { encuesta: survey, anio: year, caseId: testCase.id ?? '' } },
      body: { testCase, version },
    }),
  );
}

export async function deleteCase(
  survey: string,
  year: number,
  caseId: string,
  version: number,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.DELETE('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/cases/{caseId}', {
      params: { path: { encuesta: survey, anio: year, caseId }, query: { version } },
    }),
  );
}

export async function importRules(
  survey: string,
  year: number,
  rules: RuleSet,
  version: number,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/import', {
      params: { path: { encuesta: survey, anio: year } },
      body: { rules, version },
    }),
  );
}

/** Descarga el archivo de reglas del año tal como lo guarda el servidor. */
export async function downloadRules(survey: string, year: number): Promise<void> {
  const content = (await unwrap(
    apiClient.GET('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/export', {
      params: { path: { encuesta: survey, anio: year } },
      parseAs: 'text',
    }),
  )) as string;
  downloadTextFile(`${survey}-${year}.json`, content);
}
