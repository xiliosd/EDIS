import { describe, expect, it } from 'vitest';
import { hasCharactersOutsideLatin9, isLatin9, storedLength } from './latin9-text';

describe('latin9-text', () => {
  it('countsLatin9CharactersAsOneByte', () => {
    expect(storedLength('¿Año de gestión? ¡Pingüino! €')).toBe(29);
    expect(hasCharactersOutsideLatin9('Ñandú € Œuvre Š')).toBe(false);
  });

  it('countsCharactersOutsideLatin9AsTheirEntity', () => {
    // “ → &#8220; (7), – → &#8211; (7), 😀 → &#128512; (9).
    expect(storedLength('“a”')).toBe(15);
    expect(storedLength('–')).toBe(7);
    expect(storedLength('😀')).toBe(9);
    expect(hasCharactersOutsideLatin9('Hola “x”')).toBe(true);
  });

  it('knowsWhatLatin9ReplacedFromLatin1', () => {
    expect(isLatin9(0xa4)).toBe(false);
    expect(isLatin9(0x20ac)).toBe(true);
    expect(isLatin9(0xf1)).toBe(true);
  });

  it('countsEscapedMarkupOnlyWhenAsked', () => {
    expect(storedLength('a & <b>')).toBe(7);
    expect(storedLength('a & <b>', { escapeMarkup: true })).toBe(17);
  });

  it('reachesTheLimitJustLikeTheServer', () => {
    expect(storedLength(`${'a'.repeat(93)}–`)).toBe(100);
    expect(storedLength(`${'a'.repeat(94)}–`)).toBe(101);
  });
});
