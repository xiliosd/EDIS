import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  bindDraftsToUser,
  rotateSessionGeneration,
  saveDraft,
  SESSION_GENERATION_KEY,
  takeDraft,
  watchSessionGeneration,
} from './page-drafts';

const DRAFT_KEY = 'edi-borrador-C1';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('borradores de página y generación de sesión', () => {
  it('no recupera un borrador guardado en otra generación de sesión y lo borra', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    saveDraft('C1', { C01T: '12' });
    expect(window.sessionStorage.getItem(DRAFT_KEY)).not.toBeNull();

    // Otra pestaña ingresó con otro usuario (o cerró sesión) sin que esta lo escuchara.
    window.localStorage.setItem(SESSION_GENERATION_KEY, 'generacion-ficticia-2');

    expect(takeDraft('C1')).toBeNull();
    expect(window.sessionStorage.getItem(DRAFT_KEY)).toBeNull();
  });

  it('borra los borradores de la pestaña cuando otra cambia la generación', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    saveDraft('C1', { C01T: '12' });
    const stopWatching = watchSessionGeneration();

    window.dispatchEvent(
      new StorageEvent('storage', {
        key: SESSION_GENERATION_KEY,
        newValue: 'generacion-ficticia-2',
        storageArea: window.localStorage,
      }),
    );

    expect(window.sessionStorage.getItem(DRAFT_KEY)).toBeNull();
    stopWatching();
  });

  it('ignora los cambios de otras claves del almacenamiento', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    saveDraft('C1', { C01T: '12' });
    const stopWatching = watchSessionGeneration();

    window.dispatchEvent(
      new StorageEvent('storage', { key: 'otra-clave', storageArea: window.localStorage }),
    );

    expect(takeDraft('C1')).toEqual({ C01T: '12' });
    stopWatching();
  });

  it('el mismo usuario en la misma pestaña recupera su borrador tras ingresar de nuevo', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    saveDraft('C1', { C01T: '12' });
    const firstGeneration = window.localStorage.getItem(SESSION_GENERATION_KEY);

    await bindDraftsToUser('RESPONDENT', ' prueba05 ');

    expect(window.localStorage.getItem(SESSION_GENERATION_KEY)).not.toBe(firstGeneration);
    expect(takeDraft('C1')).toEqual({ C01T: '12' });
  });

  it('cada cambio de generación es un UUID aleatorio, sin datos del usuario', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    const generation = window.localStorage.getItem(SESSION_GENERATION_KEY) ?? '';
    rotateSessionGeneration();

    expect(generation).toMatch(/^[0-9a-f-]{36}$/);
    expect(window.localStorage.getItem(SESSION_GENERATION_KEY)).not.toBe(generation);
    expect(Object.values(window.localStorage).join(' ').toLowerCase()).not.toContain('prueba05');
  });

  it('el ingreso estrena generación aunque no se pueda calcular la huella', async () => {
    window.localStorage.setItem(SESSION_GENERATION_KEY, 'generacion-ficticia-1');
    vi.spyOn(globalThis.crypto.subtle, 'digest').mockRejectedValue(new Error('Sin SubtleCrypto'));

    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');

    expect(window.localStorage.getItem(SESSION_GENERATION_KEY)).not.toBe('generacion-ficticia-1');
    saveDraft('C1', { C01T: '12' });
    expect(takeDraft('C1')).toBeNull();
  });

  it('sin localStorage disponible no recupera borradores', async () => {
    await bindDraftsToUser('RESPONDENT', 'PRUEBA05');
    saveDraft('C1', { C01T: '12' });
    const originalGetItem = Storage.prototype.getItem;
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(function (
      this: Storage,
      key: string,
    ) {
      if (this === window.localStorage) {
        throw new DOMException('Bloqueado', 'SecurityError');
      }
      return originalGetItem.call(this, key);
    });

    expect(takeDraft('C1')).toBeNull();
    expect(window.sessionStorage.getItem(DRAFT_KEY)).toBeNull();
  });
});
