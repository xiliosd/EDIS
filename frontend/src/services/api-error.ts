import type { components } from './api-schema';

export type AnalysisReport = components['schemas']['AnalysisReport'];
export type PublicationChecklist = components['schemas']['PublicationChecklist'];

interface ProblemBody {
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
  report?: AnalysisReport;
  checklist?: PublicationChecklist;
}

const GENERIC_DETAIL = 'No fue posible completar la solicitud. Intente de nuevo.';

/** Error de la API con el contenido RFC 7807 que envía el backend. */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly fieldErrors: Record<string, string>;
  /** Informe de análisis que acompaña un 422 de reglas. */
  readonly report?: AnalysisReport;
  /** Lista de publicación que acompaña el 422 de un año que aún no puede publicarse. */
  readonly checklist?: PublicationChecklist;

  constructor(
    status: number,
    title: string,
    detail: string,
    fieldErrors: Record<string, string> = {},
    report?: AnalysisReport,
    checklist?: PublicationChecklist,
  ) {
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.fieldErrors = fieldErrors;
    this.report = report;
    this.checklist = checklist;
  }
}

export function toApiError(status: number, body: unknown): ApiError {
  const problem: ProblemBody =
    typeof body === 'object' && body !== null ? (body as ProblemBody) : {};
  return new ApiError(
    status,
    problem.title ?? 'Error',
    problem.detail ?? GENERIC_DETAIL,
    problem.errors ?? {},
    problem.report,
    problem.checklist,
  );
}

interface FetchResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/** Devuelve los datos o lanza ApiError; así las vistas manejan un solo tipo de error. */
export async function unwrap<T>(request: Promise<FetchResult<T>>): Promise<T> {
  let result: FetchResult<T>;
  try {
    result = await request;
  } catch {
    throw new ApiError(
      0,
      'Sin conexión',
      'No hay conexión con el servidor. Verifique su red e intente de nuevo.',
    );
  }
  if (!result.response.ok) {
    throw toApiError(result.response.status, result.error);
  }
  return result.data as T;
}
