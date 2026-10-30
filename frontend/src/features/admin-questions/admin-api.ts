import { apiClient, readCookie } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';

export type YearSummary = components['schemas']['YearSummary'];
export type AdminQuestionnaireView = components['schemas']['AdminQuestionnaireView'];
export type AdminPage = components['schemas']['AdminPage'];
export type AdminEntry = components['schemas']['AdminEntry'];
export type AdminEditResult = components['schemas']['AdminEditResult'];
export type NewQuestionRequest = components['schemas']['NewQuestionRequest'];
export type TablesResult = components['schemas']['TablesResult'];

export const SURVEYS = ['EDIN', 'EDID'] as const;
export type SurveyCode = (typeof SURVEYS)[number];

export function yearsQueryKey(survey: string) {
  return ['admin', 'years', survey] as const;
}

export function questionnaireQueryKey(survey: string, year: number) {
  return ['admin', 'questionnaire', survey, year] as const;
}

// La cookie XSRF-TOKEN puede faltar si el navegador la borró; se pide antes de escribir.
async function ensureCsrfToken(): Promise<void> {
  if (!readCookie('XSRF-TOKEN')) {
    await unwrap(apiClient.GET('/api/v1/auth/csrf'));
  }
}

export function fetchYears(survey: string) {
  return unwrap(
    apiClient.GET('/api/v1/admin/years/{encuesta}', { params: { path: { encuesta: survey } } }),
  );
}

export async function createYear(
  survey: string,
  year: number,
  fromYear: number,
): Promise<YearSummary> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.POST('/api/v1/admin/years/{encuesta}', {
      params: { path: { encuesta: survey } },
      body: { year, fromYear },
    }),
  );
}

export function fetchQuestionnaire(survey: string, year: number) {
  return unwrap(
    apiClient.GET('/api/v1/admin/questionnaire/{encuesta}/{anio}', {
      params: { path: { encuesta: survey, anio: year } },
    }),
  );
}

export async function editText(
  survey: string,
  year: number,
  capProyId: number,
  text: string,
  version: number,
): Promise<AdminEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/entries/{capProyId}/text', {
      params: { path: { encuesta: survey, anio: year, capProyId } },
      body: { text, version },
    }),
  );
}

export async function editHeader(
  survey: string,
  year: number,
  codEnc: number,
  text: string,
  version: number,
): Promise<AdminEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/headers/{codEnc}', {
      params: { path: { encuesta: survey, anio: year, codEnc } },
      body: { text, version },
    }),
  );
}

export async function editHelp(
  survey: string,
  year: number,
  codAyu: number,
  text: string,
  version: number,
): Promise<AdminEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/help/{codAyu}', {
      params: { path: { encuesta: survey, anio: year, codAyu } },
      body: { text, version },
    }),
  );
}

export async function reorder(
  survey: string,
  year: number,
  pageCode: string,
  capProyIds: number[],
  version: number,
): Promise<AdminEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PATCH('/api/v1/admin/questionnaire/{encuesta}/{anio}/pages/{pageCode}/order', {
      params: { path: { encuesta: survey, anio: year, pageCode } },
      body: { capProyIds, version },
    }),
  );
}

export async function addQuestion(
  survey: string,
  year: number,
  pageCode: string,
  question: NewQuestionRequest,
): Promise<AdminEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.POST('/api/v1/admin/questionnaire/{encuesta}/{anio}/pages/{pageCode}/entries', {
      params: { path: { encuesta: survey, anio: year, pageCode } },
      body: question,
    }),
  );
}

export async function deactivate(
  survey: string,
  year: number,
  capProyId: number,
  version: number,
): Promise<AdminEditResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.DELETE('/api/v1/admin/questionnaire/{encuesta}/{anio}/entries/{capProyId}', {
      params: { path: { encuesta: survey, anio: year, capProyId }, query: { version } },
    }),
  );
}

/** Script ALTER TABLE como texto plano: la aplicación nunca crea columnas, solo lo genera. */
export function fetchColumnScript(
  survey: string,
  year: number,
  capProyId: number,
  columnType?: string,
  columnLength?: number,
): Promise<string> {
  return unwrap(
    apiClient.GET(
      '/api/v1/admin/questionnaire/{encuesta}/{anio}/entries/{capProyId}/column-script',
      {
        params: {
          path: { encuesta: survey, anio: year, capProyId },
          query: { columnType, columnLength },
        },
        parseAs: 'text',
      },
    ),
  ) as Promise<string>;
}

export async function saveTables(
  survey: string,
  year: number,
  tables: Record<string, string>,
  version: number,
): Promise<TablesResult> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.PUT('/api/v1/admin/questionnaire/{encuesta}/{anio}/tables', {
      params: { path: { encuesta: survey, anio: year } },
      body: { tables, version },
    }),
  );
}

/** Script CREATE TABLE de la tabla nueva de un capítulo, como texto plano. */
export function fetchCreateTableScript(
  survey: string,
  year: number,
  chapterCode: string,
): Promise<string> {
  return unwrap(
    apiClient.GET(
      '/api/v1/admin/questionnaire/{encuesta}/{anio}/tables/{chapterCode}/create-script',
      {
        params: { path: { encuesta: survey, anio: year, chapterCode } },
        parseAs: 'text',
      },
    ),
  ) as Promise<string>;
}
