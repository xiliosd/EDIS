import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState } from 'react';
import { Button } from '../../components/Button';
import { ErrorState } from '../../components/ErrorState';
import { LinkButton } from '../../components/LinkButton';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { ApiError } from '../../services/api-error';
import { fetchProgress, finishSurvey, PROGRESS_QUERY_KEY } from './survey-api';

// OBS es VARCHAR2(1100) en EDIN_20_CAP_H_PREV_PRACT.
const MAX_OBSERVATIONS = 1100;

function ReceiptMessage({ receipt, moveFocus }: { receipt: string; moveFocus: boolean }) {
  const containerRef = useRef<HTMLDivElement>(null);
  // Al terminar, el formulario desaparece: se mueve el foco al radicado (WCAG 2.4.3).
  useEffect(() => {
    if (moveFocus) containerRef.current?.focus();
  }, [moveFocus]);
  return (
    <div
      ref={containerRef}
      tabIndex={-1}
      role="status"
      className="mt-6 rounded-xl border border-success/40 bg-surface p-4 outline-offset-2"
    >
      <p className="font-medium">Encuesta terminada. Gracias por su participación.</p>
      <p className="mt-1">
        Su radicado es <strong className="font-mono">{receipt}</strong>. Guárdelo como constancia.
      </p>
    </div>
  );
}

export function FinishPage() {
  useDocumentTitle('Terminar la encuesta');
  const queryClient = useQueryClient();
  const progress = useQuery({ queryKey: PROGRESS_QUERY_KEY, queryFn: fetchProgress });
  const [observations, setObservations] = useState('');
  const counterId = useId();
  const errorId = useId();
  const finish = useMutation({
    mutationFn: () => finishSurvey(observations),
    retry: false,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PROGRESS_QUERY_KEY }),
  });

  // El backend informa `finished` en cuanto las 9 páginas están guardadas, antes de las
  // observaciones: el radicado solo se muestra al cerrar o si ya hay observaciones registradas.
  const receipt =
    finish.data?.receipt ?? (progress.data?.observationsSaved ? progress.data.receipt : null);
  const allSaved = (progress.data?.pages ?? []).every((page) => page.state === 'SAVED');
  const observationsError =
    finish.error instanceof ApiError ? finish.error.fieldErrors.observations : undefined;
  const errorMessage = finish.isError
    ? (observationsError ??
      (finish.error instanceof ApiError
        ? finish.error.detail
        : 'No fue posible terminar. Intente de nuevo.'))
    : null;

  return (
    <section aria-labelledby="titulo-terminar">
      <h1 id="titulo-terminar" className="text-2xl font-semibold">
        Terminar la encuesta
      </h1>
      {progress.isPending && <p aria-busy="true">Cargando…</p>}
      {progress.isError && (
        <ErrorState
          message="No fue posible cargar su avance."
          onRetry={() => void progress.refetch()}
        />
      )}
      {receipt && <ReceiptMessage receipt={receipt} moveFocus={finish.isSuccess} />}
      {progress.data && !receipt && !allSaved && (
        <div className="mt-6">
          <p>Faltan páginas por guardar.</p>
          <LinkButton variant="secondary" className="mt-4" to="/encuesta">
            Volver al inicio
          </LinkButton>
        </div>
      )}
      {progress.data && !receipt && allSaved && (
        <form
          className="mt-6 grid gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            finish.mutate();
          }}
        >
          {errorMessage && (
            <p
              id={errorId}
              role="alert"
              className="rounded-xl border border-danger/40 bg-surface p-4 text-danger"
            >
              {errorMessage}
            </p>
          )}
          {progress.data.observationsSaved ? (
            <p>Sus observaciones ya fueron registradas.</p>
          ) : (
            <div>
              <label htmlFor="observaciones" className="block font-medium">
                Observaciones (opcional)
              </label>
              <textarea
                id="observaciones"
                rows={6}
                maxLength={MAX_OBSERVATIONS}
                aria-invalid={observationsError ? true : undefined}
                aria-describedby={observationsError ? `${errorId} ${counterId}` : counterId}
                value={observations}
                onChange={(event) => {
                  if (finish.isError) finish.reset();
                  setObservations(event.target.value);
                }}
                className="mt-1 block w-full rounded-lg border border-ink-muted/40 bg-surface p-3 text-base text-ink"
              />
              <p id={counterId} className="mt-1 text-sm text-ink-muted">
                {observations.length} de {MAX_OBSERVATIONS} caracteres
              </p>
            </div>
          )}
          <div>
            <Button
              type="submit"
              disabled={finish.isPending}
              aria-busy={finish.isPending || undefined}
            >
              Terminar la encuesta
            </Button>
          </div>
        </form>
      )}
    </section>
  );
}
