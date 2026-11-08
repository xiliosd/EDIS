import { describe, expect, it } from 'vitest';
import { formatCount, formatPercent } from './format-number';

describe('formatCount', () => {
  it('separatesThousandsWithDots', () => {
    expect(formatCount(1234)).toBe('1.234');
    expect(formatCount(1234567)).toBe('1.234.567');
    expect(formatCount(0)).toBe('0');
  });

  it('showsADashWhenThereIsNoValue', () => {
    expect(formatCount(undefined)).toBe('—');
  });
});

describe('formatPercent', () => {
  it('usesCommaDecimalsAndTwoDigits', () => {
    expect(formatPercent(62.5)).toBe('62,50 %');
    expect(formatPercent(0)).toBe('0,00 %');
    expect(formatPercent(100)).toBe('100,00 %');
  });

  it('showsADashWhenThereIsNoValue', () => {
    expect(formatPercent(undefined)).toBe('—');
  });
});
