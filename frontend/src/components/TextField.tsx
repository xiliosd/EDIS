import type { InputHTMLAttributes, ReactNode, Ref } from 'react';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  id: string;
  label: ReactNode;
  /** Contenido entre la etiqueta y el campo (p. ej. las ayudas de una pregunta). */
  afterLabel?: ReactNode;
  error?: string;
  /** Indicación breve bajo el campo, ligada con aria-describedby. */
  hint?: string;
  ref?: Ref<HTMLInputElement>;
}

export function TextField({
  id,
  label,
  afterLabel,
  error,
  hint,
  ref,
  ...inputProps
}: TextFieldProps) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      {afterLabel}
      <input
        id={id}
        ref={ref}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className="mt-1 block min-h-11 w-full rounded-lg border border-ink-muted/40 bg-surface px-3 text-base text-ink aria-invalid:border-danger"
        {...inputProps}
      />
      {hint && (
        <p id={hintId} className="mt-1 text-sm text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
