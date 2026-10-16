// Borrador local de una página cuando la sesión vence al guardar. Solo guarda respuestas
// (columna → valor), nunca el C_C, y vive en sessionStorage: se pierde al cerrar la pestaña.
// Cada borrador lleva la huella de su dueño para que, en un equipo compartido, nunca se
// recupere en el formulario de otro usuario (Ley 1581 de 2012).
const DRAFT_KEY_PREFIX = 'edi-borrador-';
// Huella SHA-256 (hex) del usuario que ingresó en esta pestaña; nunca el usuario en claro.
export const DRAFT_OWNER_KEY = 'edi-propietario-borradores';

interface StoredDraft {
  owner: string;
  values: Record<string, string>;
}

function draftKey(pageCode: string): string {
  return `${DRAFT_KEY_PREFIX}${pageCode}`;
}

function currentOwner(): string | null {
  try {
    return window.sessionStorage.getItem(DRAFT_OWNER_KEY);
  } catch {
    return null;
  }
}

/** Huella del usuario: SHA-256 en hex de tipo de cuenta + usuario normalizado. */
export async function draftOwnerFor(accountType: string, username: string): Promise<string> {
  const input = `${accountType}:${username.trim().toLowerCase()}`;
  const digest = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(input));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}

/** Borra todos los borradores y la huella del dueño (cierre de sesión o cambio de usuario). */
export function clearAllDrafts(): void {
  try {
    const keys: string[] = [];
    for (let index = 0; index < window.sessionStorage.length; index += 1) {
      const key = window.sessionStorage.key(index);
      if (key && (key.startsWith(DRAFT_KEY_PREFIX) || key === DRAFT_OWNER_KEY)) {
        keys.push(key);
      }
    }
    keys.forEach((key) => window.sessionStorage.removeItem(key));
  } catch {
    // Sin almacenamiento disponible: no hay borradores que borrar.
  }
}

/**
 * Al ingresar: si los borradores son de otro usuario se descartan; luego se registra al nuevo
 * dueño. Si no se puede calcular la huella, se descarta todo y no se guardarán borradores.
 */
export async function bindDraftsToUser(accountType: string, username: string): Promise<void> {
  let owner: string;
  try {
    owner = await draftOwnerFor(accountType, username);
  } catch {
    clearAllDrafts();
    return;
  }
  if (currentOwner() !== owner) {
    clearAllDrafts();
  }
  try {
    window.sessionStorage.setItem(DRAFT_OWNER_KEY, owner);
  } catch {
    // Sin almacenamiento disponible: no habrá borradores.
  }
}

export function saveDraft(pageCode: string, values: Record<string, string>): void {
  const owner = currentOwner();
  if (!owner) {
    // Sin dueño conocido no se guarda: el borrador podría terminar en otro usuario.
    return;
  }
  try {
    const draft: StoredDraft = { owner, values };
    window.sessionStorage.setItem(draftKey(pageCode), JSON.stringify(draft));
  } catch {
    // Sin almacenamiento disponible (modo privado o cuota llena): no hay borrador que guardar.
  }
}

/** Devuelve el borrador de la página si es del usuario actual (solo textos) y lo elimina. */
export function takeDraft(pageCode: string): Record<string, string> | null {
  try {
    const raw = window.sessionStorage.getItem(draftKey(pageCode));
    window.sessionStorage.removeItem(draftKey(pageCode));
    const owner = currentOwner();
    if (!raw || !owner) {
      return null;
    }
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null) {
      return null;
    }
    const draft = parsed as Partial<StoredDraft>;
    if (draft.owner !== owner || typeof draft.values !== 'object' || draft.values === null) {
      return null;
    }
    return Object.fromEntries(
      Object.entries(draft.values).filter(
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
