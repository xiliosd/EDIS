export type TextSegment =
  { kind: 'text'; text: string } | { kind: 'term'; text: string; helpIndex: number };

export interface SplitResult {
  segments: TextSegment[];
  /** Índices de las ayudas cuyo término no aparece libre en el texto. */
  missing: number[];
}

interface TermRange {
  start: number;
  end: number;
  helpIndex: number;
}

function overlaps(ranges: TermRange[], start: number, end: number): boolean {
  return ranges.some((range) => start < range.end && range.start < end);
}

/**
 * Parte el texto en tramos para enlazar cada término de ayuda. Como el str_replace del PHP
 * heredado, la comparación es exacta, pero solo se enlaza la primera aparición: repetir el mismo
 * botón en una frase solo agrega paradas de tabulación. `searchFrom` deja fuera el numeral que
 * antecede al texto de la pregunta.
 */
export function splitByTerms(text: string, terms: string[], searchFrom = 0): SplitResult {
  const ranges: TermRange[] = [];
  const missing: number[] = [];
  terms.forEach((term, helpIndex) => {
    let start = term ? text.indexOf(term, searchFrom) : -1;
    while (start >= 0 && overlaps(ranges, start, start + term.length)) {
      start = text.indexOf(term, start + 1);
    }
    if (start < 0) {
      missing.push(helpIndex);
    } else {
      ranges.push({ start, end: start + term.length, helpIndex });
    }
  });
  ranges.sort((left, right) => left.start - right.start);
  const segments: TextSegment[] = [];
  let cursor = 0;
  for (const range of ranges) {
    if (range.start > cursor) {
      segments.push({ kind: 'text', text: text.slice(cursor, range.start) });
    }
    segments.push({
      kind: 'term',
      text: text.slice(range.start, range.end),
      helpIndex: range.helpIndex,
    });
    cursor = range.end;
  }
  if (cursor < text.length) {
    segments.push({ kind: 'text', text: text.slice(cursor) });
  }
  return { segments, missing };
}
