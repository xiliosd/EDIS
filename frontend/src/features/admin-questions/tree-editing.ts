import { ApiError } from '../../services/api-error';
import type { AdminEditResult, AdminEntry } from './admin-api';

/** Lo que cada control de edición necesita del árbol: año, versión leída y cómo avisar. */
export interface TreeEditing {
  survey: string;
  year: number;
  /** Versión del archivo del año que el cliente leyó por última vez; viaja en cada edición. */
  version: number;
  onEdited: (result: AdminEditResult) => void;
  /** Tras desactivar: la pregunta sale de la lista y el árbol mueve el foco y lo anuncia. */
  onDeactivated: (result: AdminEditResult, capProyId: number, columnLabel: string) => void;
  /** 409 de versión vieja o año publicado: se muestra arriba con el botón "Recargar". */
  onConflict: (message: string) => void;
}

export const INPUT_TYPE_LABELS: Record<string, string> = {
  SINGLE_CHOICE: 'Opción única',
  CHECKBOX: 'Casillas de verificación',
  TEXT: 'Texto',
  UNKNOWN: 'Tipo desconocido',
};

const GENERIC_ERROR = 'No fue posible completar la solicitud.';

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.detail : GENERIC_ERROR;
}

export function fieldErrorsOf(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.fieldErrors : {};
}

/** Ids de las reglas que impiden desactivar: el 409 los trae como lista en errors.rules. */
export function referencingRuleIds(error: unknown): string[] {
  if (!(error instanceof ApiError) || error.status !== 409) {
    return [];
  }
  const rules: unknown = (error.fieldErrors as Record<string, unknown>).rules;
  return Array.isArray(rules) ? rules.map(String) : [];
}

/**
 * Atiende un error de edición: el 409 (otro usuario cambió el año o ya está publicado) va al aviso
 * general; el resto queda junto al control y se devuelve su mensaje.
 */
export function handleEditError(error: unknown, editing: TreeEditing): string | null {
  if (error instanceof ApiError && error.status === 409) {
    editing.onConflict(error.detail);
    return null;
  }
  return errorMessage(error);
}

const EXCERPT_LENGTH = 40;

/**
 * Nombre corto y estable de una entrada para etiquetas y anuncios: el numeral o, en los enunciados
 * sin numeral, el comienzo de su texto (no la posición, que cambia al reordenar).
 */
export function entryLabel(entry: AdminEntry): string {
  if (entry.numeral || entry.columnCode) {
    return (entry.numeral || entry.columnCode) as string;
  }
  const text = (entry.text ?? '').trim();
  const excerpt = text.length > EXCERPT_LENGTH ? `${text.slice(0, EXCERPT_LENGTH)}…` : text;
  return `enunciado «${excerpt}»`;
}
