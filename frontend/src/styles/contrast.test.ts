import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { blendOver, contrastRatio } from './contrast';

const css = readFileSync(join(import.meta.dirname, 'tokens.css'), 'utf8');

function tokensOf(block: string): Record<string, string> {
  const tokens: Record<string, string> = {};
  for (const match of block.matchAll(/--color-([a-z-]+):\s*(#[0-9a-fA-F]{6})/g)) {
    tokens[match[1]] = match[2];
  }
  return tokens;
}

const lightTheme = tokensOf(css.slice(css.indexOf('@theme'), css.indexOf('@media')));
const darkTheme = tokensOf(css.slice(css.indexOf('@media')));

// Pares texto/fondo que la interfaz usa de verdad; WCAG 2.1 AA exige 4,5:1 para texto normal.
const PAIRS: ReadonlyArray<[string, string]> = [
  ['ink', 'surface'],
  ['ink', 'surface-muted'],
  ['ink-muted', 'surface'],
  ['ink-muted', 'surface-muted'],
  ['primary', 'surface'],
  ['primary', 'surface-muted'],
  ['on-primary', 'primary'],
  ['primary-strong', 'surface'],
  ['danger', 'surface'],
  ['danger', 'surface-muted'],
  ['success', 'surface'],
  ['success', 'surface-muted'],
];

describe('contraste de los tokens', () => {
  it('calcula la razón de contraste de referencia', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 1);
    expect(contrastRatio('#ffffff', '#ffffff')).toBeCloseTo(1, 5);
  });

  it('mezcla un color con transparencia sobre un fondo', () => {
    expect(blendOver('#000000', '#ffffff', 0.5)).toBe('#808080');
    expect(blendOver('#a3134b', '#ffffff', 0)).toBe('#ffffff');
    expect(blendOver('#a3134b', '#ffffff', 1)).toBe('#a3134b');
  });

  for (const [themeName, theme] of [
    ['claro', lightTheme],
    ['oscuro', darkTheme],
  ] as const) {
    it.each(PAIRS)(`tema ${themeName}: %s sobre %s cumple 4,5:1`, (foreground, background) => {
      expect(theme[foreground], `falta --color-${foreground}`).toBeDefined();
      expect(theme[background], `falta --color-${background}`).toBeDefined();
      expect(contrastRatio(theme[foreground], theme[background])).toBeGreaterThanOrEqual(4.5);
    });

    // Enlace activo y fila resaltada del menú: texto sobre primary al 10 % encima del fondo.
    it.each([
      ['primary', 'surface'],
      ['primary', 'surface-muted'],
      ['ink', 'surface'],
      ['ink', 'surface-muted'],
    ])(`tema ${themeName}: %s sobre primary/10 encima de %s cumple 4,5:1`, (foreground, bg) => {
      const tinted = blendOver(theme['primary'], theme[bg], 0.1);
      expect(contrastRatio(theme[foreground], tinted)).toBeGreaterThanOrEqual(4.5);
    });

    // WCAG 1.4.11: el contorno de foco (color primary) necesita 3:1 contra el fondo adyacente.
    it.each(['surface', 'surface-muted'])(
      `tema ${themeName}: el foco sobre %s cumple 3:1`,
      (bg) => {
        expect(contrastRatio(theme['primary'], theme[bg])).toBeGreaterThanOrEqual(3);
      },
    );
  }
});
