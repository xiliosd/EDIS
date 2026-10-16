import type { PageDetail, QuestionnaireEntry } from './survey-api';

// D15: "0" es el valor de una pregunta que no aplicó; en una casilla significa "no marcada".
function displayValue(entry: QuestionnaireEntry, value: string | undefined): string {
  const type = entry.answer?.inputType;
  if (type === 'CHECKBOX') {
    return value === '1' ? 'Marcada' : 'No marcada';
  }
  if (value == null || value === '' || value === '0') {
    return 'No aplica';
  }
  const option = entry.answer?.options?.find((candidate) => candidate.value === value);
  return option?.label ?? value;
}

export function SavedPageSummary({ page }: { page: PageDetail }) {
  const items = (page.entries ?? []).filter(
    (entry) => entry.kind === 'ITEM' && entry.answer?.columnStatus === 'ACTIVE',
  );
  return (
    <div className="mt-6">
      <p role="status" className="rounded-xl border border-success/40 bg-surface p-4">
        Esta página ya fue guardada.
      </p>
      <ul aria-label="Respuestas guardadas" className="mt-4 grid gap-3">
        {items.map((entry) => {
          const column = entry.answer?.columnCode ?? '';
          return (
            <li key={column} className="rounded-lg bg-surface p-3">
              <p className="text-sm text-ink-muted">
                {entry.label} {entry.text}
              </p>
              <p className="font-medium">{displayValue(entry, page.answers?.[column])}</p>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
