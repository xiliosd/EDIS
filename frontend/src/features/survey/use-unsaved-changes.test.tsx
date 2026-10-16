import { renderHook } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw-server';
import { KEEP_ALIVE_INTERVAL_MS, useUnsavedChanges } from './use-unsaved-changes';

describe('useUnsavedChanges', () => {
  let sessionChecks = 0;

  beforeEach(() => {
    sessionChecks = 0;
    server.use(
      http.get('*/api/v1/auth/me', () => {
        sessionChecks += 1;
        return HttpResponse.json({ accountType: 'RESPONDENT', roles: ['ENCUESTADO'] });
      }),
    );
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('keepsSessionAliveWhileThereAreUnsavedAnswers', async () => {
    const { rerender, unmount } = renderHook(({ dirty }) => useUnsavedChanges(dirty), {
      initialProps: { dirty: true },
    });

    vi.advanceTimersByTime(KEEP_ALIVE_INTERVAL_MS);
    await vi.waitFor(() => expect(sessionChecks).toBe(1));

    rerender({ dirty: false });
    vi.advanceTimersByTime(KEEP_ALIVE_INTERVAL_MS * 2);
    expect(sessionChecks).toBe(1);
    unmount();
  });

  it('pide confirmación al cerrar la pestaña solo si hay cambios', () => {
    const { rerender } = renderHook(({ dirty }) => useUnsavedChanges(dirty), {
      initialProps: { dirty: true },
    });
    const dirtyEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(dirtyEvent);
    expect(dirtyEvent.defaultPrevented).toBe(true);

    rerender({ dirty: false });
    const cleanEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(cleanEvent);
    expect(cleanEvent.defaultPrevented).toBe(false);
  });
});
