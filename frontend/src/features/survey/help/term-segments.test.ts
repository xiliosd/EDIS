import { describe, expect, it } from 'vitest';
import { splitByTerms } from './term-segments';

describe('splitByTerms', () => {
  it('marca solo la primera aparición exacta de cada término', () => {
    expect(splitByTerms('Edad en años, años cumplidos', ['años'])).toEqual({
      segments: [
        { kind: 'text', text: 'Edad en ' },
        { kind: 'term', text: 'años', helpIndex: 0 },
        { kind: 'text', text: ', años cumplidos' },
      ],
      missing: [],
    });
  });

  it('distingue mayúsculas como el str_replace heredado y reporta los ausentes', () => {
    expect(splitByTerms('Edad en años', ['Años', 'Edad'])).toEqual({
      segments: [
        { kind: 'term', text: 'Edad', helpIndex: 1 },
        { kind: 'text', text: ' en años' },
      ],
      missing: [0],
    });
  });

  it('busca desde la posición indicada para no enlazar el numeral', () => {
    const { segments } = splitByTerms('B02 Nivel B02', ['B02'], 4);
    expect(segments).toEqual([
      { kind: 'text', text: 'B02 Nivel ' },
      { kind: 'term', text: 'B02', helpIndex: 0 },
    ]);
  });

  it('no superpone términos: el segundo usa la siguiente aparición libre o queda ausente', () => {
    expect(splitByTerms('nivel del nivel', ['nivel del', 'nivel', 'del'])).toEqual({
      segments: [
        { kind: 'term', text: 'nivel del', helpIndex: 0 },
        { kind: 'text', text: ' ' },
        { kind: 'term', text: 'nivel', helpIndex: 1 },
      ],
      missing: [2],
    });
  });

  it('un término vacío nunca se enlaza', () => {
    expect(splitByTerms('Texto', [''])).toEqual({
      segments: [{ kind: 'text', text: 'Texto' }],
      missing: [0],
    });
  });
});
