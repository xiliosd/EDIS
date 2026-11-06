/**
 * Cálculo aproximado, en el navegador, del espacio que un texto ocupará en la base real
 * (WE8ISO8859P15: un byte por carácter de Latin-9). Lo que Latin-9 no tiene (comillas tipográficas,
 * rayas, emojis) el servidor lo guarda como la entidad `&#N;`, que ocupa todos sus caracteres. El
 * servidor es la autoridad: esto solo evita un viaje cuando el texto claramente no cabe.
 */

// Posiciones de Latin-1 que ISO-8859-15 reemplazó (¤ ¦ ¨ ´ ¸ ¼ ½ ¾).
const REPLACED_IN_LATIN9 = new Set([0xa4, 0xa6, 0xa8, 0xb4, 0xb8, 0xbc, 0xbd, 0xbe]);
// Lo que ocupa esas posiciones en Latin-9: € Š š Ž ž Œ œ Ÿ.
const ADDED_IN_LATIN9 = new Set([0x20ac, 0x160, 0x161, 0x17d, 0x17e, 0x152, 0x153, 0x178]);

export function isLatin9(codePoint: number): boolean {
  if (codePoint <= 0xff) {
    return !REPLACED_IN_LATIN9.has(codePoint);
  }
  return ADDED_IN_LATIN9.has(codePoint);
}

/** Si el texto tiene algún carácter que la base guardará como código. */
export function hasCharactersOutsideLatin9(text: string): boolean {
  for (const character of text) {
    if (!isLatin9(character.codePointAt(0) ?? 0)) {
      return true;
    }
  }
  return false;
}

/**
 * Bytes que ocupará el texto guardado. Con `escapeMarkup` cuenta además `&amp;`, `&lt;` y `&gt;`,
 * como guarda los textos del cuestionario el servidor.
 */
export function storedLength(text: string, options: { escapeMarkup?: boolean } = {}): number {
  let length = 0;
  for (const character of text) {
    const codePoint = character.codePointAt(0) ?? 0;
    if (options.escapeMarkup && character === '&') {
      length += '&amp;'.length;
    } else if (options.escapeMarkup && (character === '<' || character === '>')) {
      length += '&lt;'.length;
    } else if (isLatin9(codePoint)) {
      length += 1;
    } else {
      length += `&#${codePoint};`.length;
    }
  }
  return length;
}
