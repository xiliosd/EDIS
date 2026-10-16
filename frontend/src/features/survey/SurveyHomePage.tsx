import { useQuery } from '@tanstack/react-query';
import { ErrorState } from '../../components/ErrorState';
import { LinkButton } from '../../components/LinkButton';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { useSession } from '../auth/use-session';
import { fetchProgress, PROGRESS_QUERY_KEY, type PageStatus } from './survey-api';

const STATE_LABELS: Record<string, string> = {
  SAVED: 'Guardada',
  AVAILABLE: 'Disponible',
  LOCKED: 'Bloqueada',
};

const STATE_CLASSES: Record<string, string> = {
  SAVED: 'border-success/40 text-success',
  AVAILABLE: 'border-primary/40 text-primary',
  LOCKED: 'border-ink-muted/40 text-ink-muted',
};

function partLabel(pages: PageStatus[], page: PageStatus): string | undefined {
  const sameChapter = pages.filter((other) => other.chapterCode === page.chapterCode);
  if (sameChapter.length < 2) {
    return undefined;
  }
  return `Parte ${sameChapter.indexOf(page) + 1} de ${sameChapter.length}`;
}

export function SurveyHomePage() {
  const { data: session } = useSession();
  const progress = useQuery({ queryKey: PROGRESS_QUERY_KEY, queryFn: fetchProgress });
  const title = `Encuesta ${session?.surveyCode ?? ''} ${session?.surveyYear ?? ''}`.trim();
  useDocumentTitle(title);

  const pages = progress.data?.pages ?? [];
  const nextPage = pages.find((page) => page.state === 'AVAILABLE');
  const allSaved = pages.length > 0 && pages.every((page) => page.state === 'SAVED');

  return (
    <section aria-labelledby="titulo-encuesta">
      <h1 id="titulo-encuesta" className="text-2xl font-semibold">
        {title}
      </h1>
      {progress.isPending && (
        <div aria-hidden="true" className="mt-6 grid gap-3 sm:grid-cols-2">
          {[0, 1, 2, 3].map((index) => (
            <div key={index} className="h-20 animate-pulse rounded-xl bg-surface" />
          ))}
        </div>
      )}
      {progress.isError && (
        <ErrorState
          message="No fue posible cargar su avance."
          onRetry={() => void progress.refetch()}
        />
      )}
      {progress.data && (
        <>
          {progress.data.finished ? (
            // D18: la encuesta termina con las 9 páginas guardadas; las observaciones son opcionales.
            <>
              <div
                role="status"
                className="mt-6 rounded-xl border border-success/40 bg-surface p-4"
              >
                <p className="font-medium">Encuesta terminada. Gracias por su participación.</p>
                <p className="mt-1">
                  Su radicado es <strong className="font-mono">{progress.data.receipt}</strong>
                </p>
              </div>
              {!progress.data.observationsSaved && (
                <LinkButton variant="secondary" className="mt-4" to="/encuesta/terminar">
                  Agregar observaciones (opcional)
                </LinkButton>
              )}
            </>
          ) : (
            <div className="mt-4">
              <p className="text-ink-muted">
                Responda las páginas en orden. Cada página se guarda una sola vez.
              </p>
              {nextPage && (
                <LinkButton className="mt-4" to={`/encuesta/pagina/${nextPage.pageCode}`}>
                  Continuar
                </LinkButton>
              )}
              {allSaved && (
                <LinkButton className="mt-4" to="/encuesta/terminar">
                  Terminar la encuesta
                </LinkButton>
              )}
            </div>
          )}
          <ol className="mt-6 grid gap-3 sm:grid-cols-2">
            {pages.map((page) => (
              <li
                key={page.pageCode}
                className="rounded-xl border border-ink-muted/20 bg-surface p-4"
              >
                <span className="text-sm text-ink-muted">{page.module}</span>
                <p className="font-medium">
                  {page.chapterCode}. {page.chapterTitle}
                </p>
                <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
                  {partLabel(pages, page) && (
                    <span className="text-ink-muted">{partLabel(pages, page)}</span>
                  )}
                  <span
                    className={`rounded-full border px-2 py-0.5 ${STATE_CLASSES[page.state ?? 'LOCKED']}`}
                  >
                    {STATE_LABELS[page.state ?? 'LOCKED']}
                  </span>
                </div>
              </li>
            ))}
          </ol>
        </>
      )}
    </section>
  );
}
