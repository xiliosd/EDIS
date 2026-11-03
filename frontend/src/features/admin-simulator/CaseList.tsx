import { useState } from 'react';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import type { CaseResult, TestCase } from '../admin-rules/rules-api';

interface CaseListProps {
  cases: TestCase[];
  results: CaseResult[];
  /** Con el año publicado los casos solo se consultan. */
  editable: boolean;
  onOpen: (testCase: TestCase) => void;
  onDelete: (testCase: TestCase) => void;
}

// Cada estado lleva texto e ícono además del color (WCAG 1.4.1).
const STATUS_VIEW: Record<string, { label: string; icon: string; classes: string }> = {
  pasa: { label: 'Pasa', icon: '✓', classes: 'border-success/50 text-success' },
  falla: { label: 'Falla', icon: '✗', classes: 'border-danger/50 text-danger' },
  por_revisar: { label: 'Por revisar', icon: '⚠', classes: 'border-warning/60 text-warning' },
};
const NO_RESULT = {
  label: 'Sin evaluar',
  icon: '–',
  classes: 'border-ink-muted/40 text-ink-muted',
};

/** Casos de prueba del año con su resultado; cada fila es un ancla `caso-<id>`. */
export function CaseList({ cases, results, editable, onOpen, onDelete }: CaseListProps) {
  const [toDelete, setToDelete] = useState<TestCase | null>(null);
  const resultOf = new Map(results.map((result) => [result.id, result]));

  if (cases.length === 0) {
    return <p className="mt-3">Este año todavía no tiene casos de prueba.</p>;
  }
  return (
    <>
      <ul className="mt-3 grid gap-3">
        {cases.map((testCase) => {
          const caseId = testCase.id ?? '';
          const result = resultOf.get(caseId);
          const status =
            STATUS_VIEW[result?.status ?? (testCase.porRevisar ? 'por_revisar' : '')] ?? NO_RESULT;
          return (
            <li
              key={caseId}
              id={`caso-${caseId}`}
              tabIndex={-1}
              className="scroll-mt-4 rounded-xl border border-ink-muted/30 p-4"
            >
              <p className="font-medium">
                <code>{caseId}</code> · {testCase.nombre}
              </p>
              <p className="text-sm text-ink-muted">Página {testCase.pagina}</p>
              <p
                className={`mt-2 inline-flex items-center gap-2 rounded-lg border px-2 py-1 font-medium ${status.classes}`}
              >
                <span aria-hidden="true">{status.icon}</span>
                <span>{status.label}</span>
              </p>
              {(result?.differences ?? []).length > 0 && (
                <ul className="mt-2 ml-6 list-disc">
                  {(result?.differences ?? []).map((difference) => (
                    <li key={difference}>{difference}</li>
                  ))}
                </ul>
              )}
              {result?.status === 'por_revisar' && (
                <p className="mt-2 text-ink-muted">
                  Una pregunta del caso cambió: ábralo, revíselo y vuelva a guardarlo.
                </p>
              )}
              <div className="mt-3 flex flex-wrap gap-3">
                <Button
                  variant="secondary"
                  aria-label={`Abrir en el simulador el caso ${caseId}`}
                  onClick={() => onOpen(testCase)}
                >
                  Abrir en el simulador
                </Button>
                <Button
                  variant="secondary"
                  aria-label={`Eliminar caso ${caseId}`}
                  disabled={!editable}
                  onClick={() => setToDelete(testCase)}
                >
                  Eliminar
                </Button>
              </div>
            </li>
          );
        })}
      </ul>
      {toDelete && (
        <ConfirmDialog
          open
          title={`Eliminar el caso ${toDelete.id}`}
          confirmLabel="Eliminar"
          cancelLabel="Cancelar"
          onCancel={() => setToDelete(null)}
          onConfirm={() => {
            onDelete(toDelete);
            setToDelete(null);
          }}
        >
          <p>{toDelete.nombre}</p>
          <p className="mt-2">Esta acción no se puede deshacer.</p>
        </ConfirmDialog>
      )}
    </>
  );
}
