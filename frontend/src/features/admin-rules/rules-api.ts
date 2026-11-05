import { apiClient, ensureCsrfToken } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';
import { downloadTextFile } from '../../services/download-file';

export type RulesEditorView = components['schemas']['RulesEditorView'];
export type RulesEditResult = components['schemas']['RulesEditResult'];
export type { AnalysisReport } from '../../services/api-error';
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

/** Cuelga de la clave de las reglas: invalidar las reglas también relee el JSON exportado. */
export function rulesExportQueryKey(survey: string, year: number) {
  return [...rulesQueryKey(survey, year), 'export'] as const;
}

export function simulatorQueryKey(survey: string, year: number) {
  return ['admin', 'simulator', survey, year] as const;
}

export function fetchRules(survey: string, year: number): Promise<RulesEditorView> {
  return unwrap(
    apiClient.GET('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules', {
      params: { path: { encuesta: survey, anio: year } },
    }),
  );
}

/** Archivo de reglas del año tal como lo guarda el servidor, para la vista JSON. */
export async function fetchRulesExport(survey: string, year: number): Promise<string> {
  return (await unwrap(
    apiClient.GET('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/export', {
      params: { path: { encuesta: survey, anio: year } },
      parseAs: 'text',
    }),
  )) as string;
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
  motivo?: string,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/skips/{ruleId}', {
      params: { path: { encuesta: survey, anio: year, ruleId: rule.id ?? '' } },
      body: { rule, version, motivo },
    }),
  );
}

export async function deleteSkip(
  survey: string,
  year: number,
  ruleId: string,
  version: number,
  motivo?: string,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.DELETE('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/skips/{ruleId}', {
      params: { path: { encuesta: survey, anio: year, ruleId }, query: { version } },
      // El motivo va en el cuerpo, no en la URL: la URL queda en los logs de acceso.
      body: { motivo },
    }),
  );
}

export async function saveValidation(
  survey: string,
  year: number,
  column: string,
  validation: FieldValidation,
  version: number,
  motivo?: string,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/validations/{column}', {
      params: { path: { encuesta: survey, anio: year, column } },
      body: { validation, version, motivo },
    }),
  );
}

export async function deleteValidation(
  survey: string,
  year: number,
  column: string,
  version: number,
  motivo?: string,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.DELETE('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/validations/{column}', {
      params: { path: { encuesta: survey, anio: year, column }, query: { version } },
      // El motivo va en el cuerpo, no en la URL: la URL queda en los logs de acceso.
      body: { motivo },
    }),
  );
}

export async function saveCase(
  survey: string,
  year: number,
  testCase: TestCase,
  version: number,
  motivo?: string,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/cases/{caseId}', {
      params: { path: { encuesta: survey, anio: year, caseId: testCase.id ?? '' } },
      body: { testCase, version, motivo },
    }),
  );
}

export async function deleteCase(
  survey: string,
  year: number,
  caseId: string,
  version: number,
  motivo?: string,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.DELETE('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/cases/{caseId}', {
      params: { path: { encuesta: survey, anio: year, caseId }, query: { version } },
      // El motivo va en el cuerpo, no en la URL: la URL queda en los logs de acceso.
      body: { motivo },
    }),
  );
}

export async function importRules(
  survey: string,
  year: number,
  rules: RuleSet,
  version: number,
  motivo?: string,
): Promise<RulesEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/import', {
      params: { path: { encuesta: survey, anio: year } },
      body: { rules, version, motivo },
    }),
  );
}

/** Descarga el archivo de reglas del año tal como lo guarda el servidor. */
export async function downloadRules(survey: string, year: number): Promise<void> {
  downloadTextFile(`${survey}-${year}.json`, await fetchRulesExport(survey, year));
}
