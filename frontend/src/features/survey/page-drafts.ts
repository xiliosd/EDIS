// Borrador local de una página cuando la sesión vence al guardar. Solo guarda respuestas
// (columna → valor), nunca el C_C, y vive en sessionStorage: se pierde al cerrar la pestaña.
const DRAFT_KEY_PREFIX = 'edi-borrador-';

function draftKey(pageCode: string): string {
  return `${DRAFT_KEY_PREFIX}${pageCode}`;
}

export function saveDraft(pageCode: string, values: Record<string, string>): void {
  try {
    window.sessionStorage.setItem(draftKey(pageCode), JSON.stringify(values));
  } catch {
    // Sin almacenamiento disponible (modo privado o cuota llena): no hay borrador que guardar.
  }
}

/** Devuelve el borrador de la página (solo valores de texto) y lo elimina. */
export function takeDraft(pageCode: string): Record<string, string> | null {
  try {
    const raw = window.sessionStorage.getItem(draftKey(pageCode));
    window.sessionStorage.removeItem(draftKey(pageCode));
    if (!raw) {
      return null;
    }
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return null;
    }
    return Object.fromEntries(
      Object.entries(parsed).filter(
        (pair): pair is [string, string] => typeof pair[1] === 'string',
      ),
    );
  } catch {
    return null;
  }
}

export function clearDraft(pageCode: string): void {
  try {
    window.sessionStorage.removeItem(draftKey(pageCode));
  } catch {
    // Sin almacenamiento disponible: no hay borrador que borrar.
  }
}
