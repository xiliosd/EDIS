import type { Ref } from 'react';
import { fieldId } from './questions/QuestionField';

interface ErrorSummaryProps {
  errors: Record<string, string>;
  ref?: Ref<HTMLDivElement>;
}

/** Resumen de errores enfocable con enlaces a cada campo (patrón GOV.UK, WCAG 3.3.1). */
export function ErrorSummary({ errors, ref }: ErrorSummaryProps) {
  return (
    <div
      ref={ref}
      role="alert"
      tabIndex={-1}
      className="mt-6 rounded-xl border border-danger/40 bg-surface p-4"
    >
      <p className="font-medium text-danger">Revise estas preguntas antes de guardar:</p>
      <ul className="mt-2 list-disc pl-6">
        {Object.entries(errors).map(([column, message]) => (
          <li key={column}>
            <a href={`#${fieldId(column)}`} className="text-primary underline">
              {column}: {message}
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
