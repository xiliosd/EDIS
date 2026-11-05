import { useCallback, useState } from 'react';

export const REASON_MIN_LENGTH = 10;
export const REASON_MAX_LENGTH = 500;

/** El motivo recortado si tiene entre 10 y 500 caracteres; es lo que exige el servidor. */
export function validReason(text: string): string | undefined {
  const trimmed = text.trim();
  return trimmed.length >= REASON_MIN_LENGTH && trimmed.length <= REASON_MAX_LENGTH
    ? trimmed
    : undefined;
}

export interface CorrectionReason {
  text: string;
  setText: (text: string) => void;
  /** Motivo listo para enviar (recortado y válido), o undefined mientras no lo sea. */
  motivo: string | undefined;
  /** Se vacía tras cada guardado exitoso: cada corrección necesita su propio motivo. */
  clear: () => void;
}

/** Motivo de corrección de una página: vive solo en memoria y se pierde al navegar. */
export function useCorrectionReason(): CorrectionReason {
  const [text, setText] = useState('');
  const clear = useCallback(() => setText(''), []);
  return { text, setText, motivo: validReason(text), clear };
}
