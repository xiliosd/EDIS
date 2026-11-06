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
  /**
   * Año publicado: solo se corrigen textos, y cada guardado necesita un motivo válido. Lo
   * estructural (agregar, desactivar, reordenar, tablas) queda deshabilitado.
   */
  correction: boolean;
  /** Motivo recortado y válido que viaja en la corrección; undefined mientras no lo sea. */
  motivo?: string;
  /** Tras guardar una corrección: la página vacía el motivo para que el siguiente sea nuevo. */
  onSaved?: () => void;
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

/** Mientras falte el motivo válido, una corrección no se puede guardar. */
export function saveBlocked(editing: TreeEditing): boolean {
  return editing.correction && !editing.motivo;
}

/**
 * Límites de las columnas reales (WE8ISO8859P15, en bytes), los mismos que aplica el servidor.
 * El numeral va a FRM_CAP_PROY.NUMERAL, VARCHAR2(5).
 */
export const TEXT_LIMITS = {
  question: { maxLength: 1000, name: 'El texto de la pregunta' },
  header: { maxLength: 500, name: 'El encabezado' },
  helpWord: { maxLength: 100, name: 'La palabra de ayuda' },
} as const;

export type TextLimit = (typeof TEXT_LIMITS)[keyof typeof TEXT_LIMITS];

export const NUMERAL_MAX_LENGTH = 5;

export const SPECIAL_CHARACTERS_NOTE =
  'Los caracteres especiales que la base no admite (comillas tipográficas, rayas, emojis) se ' +
  'guardan como código, sin perder el texto, y ocupan más espacio.';

/** El mismo mensaje del servidor, para avisar antes de enviar un texto que no cabe. */
export function tooLongMessage(limit: TextLimit): string {
  return (
    `${limit.name} admite hasta ${limit.maxLength} caracteres (los caracteres especiales que ` +
    'se guardan como código cuentan como varios).'
  );
}

export const DRAFT_ONLY_NOTE = 'Solo en borrador.';
export const REASON_REQUIRED_NOTE = 'Escriba el motivo de la corrección para guardar.';

/** Reglas y casos de prueba que impiden desactivar una pregunta. */
export interface DeactivationBlockers {
  rules: string[];
  cases: string[];
}

function idsOf(value: unknown): string[] {
  return Array.isArray(value) ? value.map(String) : [];
}

/**
 * Lo que impide desactivar: el 409 trae las listas en errors.rules y errors.cases. Devuelve null si
 * el error es otro (por ejemplo, otro usuario cambió el año) o si ambas listas vienen vacías.
 */
export function deactivationBlockers(error: unknown): DeactivationBlockers | null {
  if (!(error instanceof ApiError) || error.status !== 409) {
    return null;
  }
  const errors = error.fieldErrors as Record<string, unknown>;
  const blockers = { rules: idsOf(errors.rules), cases: idsOf(errors.cases) };
  return blockers.rules.length > 0 || blockers.cases.length > 0 ? blockers : null;
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
