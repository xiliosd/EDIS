// Borrador local de una página cuando la sesión vence al guardar. Solo guarda respuestas
// (columna → valor), nunca el C_C, y vive en sessionStorage: se pierde al cerrar la pestaña.
// Cada borrador lleva la huella de su dueño para que, en un equipo compartido, nunca se
// recupere en el formulario de otro usuario (Ley 1581 de 2012).
const DRAFT_KEY_PREFIX = 'edi-borrador-';
// Huella SHA-256 (hex) del usuario que ingresó en esta pestaña; nunca el usuario en claro.
export const DRAFT_OWNER_KEY = 'edi-propietario-borradores';
// sessionStorage es de cada pestaña: si otra pestaña ingresa con otro usuario o cierra sesión,
// esta no se entera por la huella. Cada ingreso y cada cierre escriben en localStorage (común a
// todas las pestañas) una "generación" aleatoria sin datos personales; un borrador solo se
// recupera si la pestaña lo guardó en la generación vigente.
export const SESSION_GENERATION_KEY = 'edi-generacion-sesion';
// Generación vigente cuando esta pestaña ingresó; vive junto a la huella del dueño.
const TAB_GENERATION_KEY = 'edi-generacion-borradores';

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

function sharedGeneration(): string | null {
  try {
    return window.localStorage.getItem(SESSION_GENERATION_KEY);
  } catch {
    return null;
  }
}

function tabGeneration(): string | null {
  try {
    return window.sessionStorage.getItem(TAB_GENERATION_KEY);
  } catch {
    return null;
  }
}

/**
 * Escribe una generación nueva para que las demás pestañas descarten sus borradores. Devuelve la
 * generación escrita, o null si no se pudo (sin almacenamiento o sin generador aleatorio).
 */
export function rotateSessionGeneration(): string | null {
  try {
    const generation = globalThis.crypto.randomUUID();
    window.localStorage.setItem(SESSION_GENERATION_KEY, generation);
    return generation;
  } catch {
    return null;
  }
}

/** Escucha los cambios de generación de otras pestañas y descarta los borradores de esta. */
export function watchSessionGeneration(): () => void {
  const onStorage = (event: StorageEvent) => {
    // key null: otra pestaña vació el almacenamiento; también se descartan por seguridad.
    if (event.key === SESSION_GENERATION_KEY || event.key === null) {
      clearAllDrafts();
    }
  };
  window.addEventListener('storage', onStorage);
  return () => window.removeEventListener('storage', onStorage);
}

/** Borra todos los borradores y la huella del dueño (cierre de sesión o cambio de usuario). */
export function clearAllDrafts(): void {
  try {
    const keys: string[] = [];
    for (let index = 0; index < window.sessionStorage.length; index += 1) {
      const key = window.sessionStorage.key(index);
      if (
        key &&
        (key.startsWith(DRAFT_KEY_PREFIX) || key === DRAFT_OWNER_KEY || key === TAB_GENERATION_KEY)
      ) {
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
    // Sin huella no habrá borradores aquí, pero las demás pestañas igual deben descartar los suyos.
    clearAllDrafts();
    rotateSessionGeneration();
    return;
  }
  if (currentOwner() !== owner) {
    clearAllDrafts();
  }
  // Cada ingreso estrena generación: las demás pestañas descartan sus borradores. Esta pestaña
  // la adopta, así el mismo usuario que vuelve a ingresar aquí conserva los suyos (los de otro
  // usuario ya se borraron arriba).
  const generation = rotateSessionGeneration();
  try {
    window.sessionStorage.setItem(DRAFT_OWNER_KEY, owner);
    if (generation) {
      window.sessionStorage.setItem(TAB_GENERATION_KEY, generation);
    } else {
      window.sessionStorage.removeItem(TAB_GENERATION_KEY);
    }
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

/** Indica, sin consumirlo, si hay un borrador de la página que se recuperaría al volver. */
export function hasRecoverableDraft(pageCode: string): boolean {
  const generation = tabGeneration();
  const owner = currentOwner();
  if (!generation || !owner || generation !== sharedGeneration()) {
    return false;
  }
  try {
    const raw = window.sessionStorage.getItem(draftKey(pageCode));
    const parsed: unknown = raw ? JSON.parse(raw) : null;
    return typeof parsed === 'object' && parsed !== null && (parsed as StoredDraft).owner === owner;
  } catch {
    return false;
  }
}

/**
 * Devuelve el borrador de la página si es del usuario actual y de la generación vigente (solo
 * textos) y lo elimina.
 */
export function takeDraft(pageCode: string): Record<string, string> | null {
  const generation = tabGeneration();
  if (!generation || generation !== sharedGeneration()) {
    // Otra pestaña ingresó o cerró sesión después: nada de esta pestaña se recupera.
    clearAllDrafts();
    return null;
  }
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
