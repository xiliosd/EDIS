import { useQuery } from '@tanstack/react-query';
import { ErrorState } from '../../components/ErrorState';
import { apiClient } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import { useSession } from '../auth/use-session';

export function SurveyHomePage() {
  const { data: session } = useSession();
  const chapters = useQuery({
    queryKey: ['chapters'],
    queryFn: () => unwrap(apiClient.GET('/api/v1/survey/chapters')),
  });

  return (
    <section aria-labelledby="titulo-encuesta">
      <h1 id="titulo-encuesta" className="text-2xl font-semibold">
        Encuesta {session?.surveyCode} {session?.surveyYear}
      </h1>
      <p className="mt-2 text-ink-muted">Estos son los capítulos del cuestionario.</p>
      {chapters.isPending && (
        <div aria-hidden="true" className="mt-6 grid gap-3 sm:grid-cols-2">
          {[0, 1, 2, 3].map((index) => (
            <div key={index} className="h-20 animate-pulse rounded-xl bg-surface" />
          ))}
        </div>
      )}
      {chapters.isError && (
        <ErrorState
          message="No fue posible cargar los capítulos."
          onRetry={() => void chapters.refetch()}
        />
      )}
      {chapters.data && (
        <ol className="mt-6 grid gap-3 sm:grid-cols-2">
          {chapters.data.map((chapter) => (
            <li key={chapter.code} className="rounded-xl border border-ink-muted/20 bg-surface p-4">
              <span className="text-sm text-ink-muted">{chapter.module}</span>
              <p className="font-medium">
                {chapter.code}. {chapter.title}
              </p>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
