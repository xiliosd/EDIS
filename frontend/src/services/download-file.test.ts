import { afterEach, describe, expect, it, vi } from 'vitest';
import { downloadTextFile, serverFileName } from './download-file';

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe('downloadTextFile', () => {
  it('clicksAnAttachedLinkAndRevokesTheUrlLater', () => {
    vi.useFakeTimers();
    const revoke = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { value: () => 'blob:x', configurable: true });
    Object.defineProperty(URL, 'revokeObjectURL', { value: revoke, configurable: true });
    let attached = false;
    let fileName = '';
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      attached = document.body.contains(this);
      fileName = this.download;
    });

    downloadTextFile('columna-C30A.sql', 'ALTER TABLE T ADD (C30A VARCHAR2(2));');

    expect(attached).toBe(true);
    expect(fileName).toBe('columna-C30A.sql');
    expect(document.querySelector('a[download]')).toBeNull();
    expect(revoke).not.toHaveBeenCalled();
    vi.runAllTimers();
    expect(revoke).toHaveBeenCalledWith('blob:x');
  });
});

describe('serverFileName', () => {
  it('returnsASimpleNameWithTheExpectedExtension', () => {
    expect(serverFileName('attachment; filename="cobertura-GENERAL-2026.xlsx"', '.xlsx')).toBe(
      'cobertura-GENERAL-2026.xlsx',
    );
  });

  it('rejectsOtherExtensionsPathsAndMissingHeaders', () => {
    expect(serverFileName('attachment; filename="paquete.zip"', '.xlsx')).toBeUndefined();
    expect(serverFileName('attachment; filename="../x.xlsx"', '.xlsx')).toBeUndefined();
    expect(serverFileName(null, '.xlsx')).toBeUndefined();
  });
});
