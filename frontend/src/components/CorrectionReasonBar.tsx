import { useId } from 'react';
import { REASON_MAX_LENGTH, REASON_MIN_LENGTH, validReason } from '../hooks/use-correction-reason';

interface CorrectionReasonBarProps {
  value: string;
  onChange: (value: string) => void;
}

/**
 * Motivo que acompaña cada corrección de un año publicado: queda en la bitácora del archivo, por
 * eso el aviso de no escribir datos personales.
 */
export function CorrectionReasonBar({ value, onChange }: CorrectionReasonBarProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const invalid = value.trim() !== '' && validReason(value) === undefined;
  return (
    <section
      aria-label="Corrección de un año publicado"
      className="mt-4 rounded-xl border border-ink-muted/30 bg-surface p-4"
    >
      <p className="font-medium">
        El año está publicado: solo se permiten correcciones de textos, saltos y validaciones.
      </p>
      <label htmlFor={id} className="mt-3 block text-sm font-medium">
        Motivo de la corrección
      </label>
      <textarea
        id={id}
        value={value}
        required
        minLength={REASON_MIN_LENGTH}
        maxLength={REASON_MAX_LENGTH}
        rows={2}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={invalid ? true : undefined}
        aria-describedby={invalid ? `${hintId} ${errorId}` : hintId}
        className="mt-1 block w-full rounded-lg border border-ink-muted/40 bg-surface px-3 py-2 text-base text-ink aria-invalid:border-danger"
      />
      <p id={hintId} className="mt-1 text-sm text-ink-muted">
        No escriba datos personales en el motivo. Entre {REASON_MIN_LENGTH} y {REASON_MAX_LENGTH}{' '}
        caracteres.
      </p>
      {invalid && (
        <p id={errorId} className="mt-1 text-sm text-danger">
          {value.trim().length < REASON_MIN_LENGTH
            ? `Escriba al menos ${REASON_MIN_LENGTH} caracteres.`
            : `Use hasta ${REASON_MAX_LENGTH} caracteres.`}
        </p>
      )}
    </section>
  );
}
