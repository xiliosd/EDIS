import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorState } from '../../components/ErrorState';
import { TextField } from '../../components/TextField';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { ApiError } from '../../services/api-error';
import {
  createYear,
  fetchYears,
  SURVEYS,
  yearsQueryKey,
  type SurveyCode,
  type YearSummary,
} from './admin-api';

const STATE_LABELS: Record<string, string> = { borrador: 'Borrador', publicado: 'Publicado' };
const YEAR_PATTERN = /^\d{4}$/;
const YEAR_ERROR = 'Escriba un año de cuatro cifras.';

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.detail : 'No fue posible completar la solicitud.';
}

function latestYear(years: YearSummary[]): number {
  return Math.max(...years.map((item) => item.year ?? 0));
}

function suggestSourceYear(years: YearSummary[]): string {
  const published = years.filter((item) => item.state === 'publicado');
  return String(latestYear(published.length > 0 ? published : years));
}

export function YearsPage() {
  useDocumentTitle('Años del cuestionario');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [survey, setSurvey] = useState<SurveyCode>('EDIN');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [newYear, setNewYear] = useState('');
  const [fromYear, setFromYear] = useState('');
  // Tras pulsar "Crear" se muestran también los errores de los campos vacíos (WCAG 3.3.1).
  const [submitted, setSubmitted] = useState(false);
  const newYearRef = useRef<HTMLInputElement>(null);
  const fromYearRef = useRef<HTMLInputElement>(null);

  const yearsQuery = useQuery({
    queryKey: yearsQueryKey(survey),
    queryFn: () => fetchYears(survey),
  });
  const years = yearsQuery.data ?? [];

  const createMutation = useMutation({
    mutationFn: () => createYear(survey, Number(newYear), Number(fromYear)),
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: yearsQueryKey(survey) });
      setDialogOpen(false);
      void navigate(`/admin/cuestionario/${survey}/${created.year}`);
    },
    // El detalle (409/422) se muestra en la página, no detrás del diálogo modal.
    onError: () => setDialogOpen(false),
  });

  function openDialog() {
    createMutation.reset();
    setSubmitted(false);
    setNewYear(years.length > 0 ? String(latestYear(years) + 1) : '');
    setFromYear(years.length > 0 ? suggestSourceYear(years) : '');
    setDialogOpen(true);
  }

  const newYearValid = YEAR_PATTERN.test(newYear);
  const fromYearValid = YEAR_PATTERN.test(fromYear);

  function yearError(value: string, valid: boolean): string | undefined {
    return (submitted || value) && !valid ? YEAR_ERROR : undefined;
  }

  function confirmCreate() {
    if (newYearValid && fromYearValid) {
      createMutation.mutate();
      return;
    }
    setSubmitted(true);
    (newYearValid ? fromYearRef : newYearRef).current?.focus();
  }

  return (
    <section aria-labelledby="titulo-anios">
      <h1 id="titulo-anios" className="text-2xl font-semibold">
        Años del cuestionario
      </h1>

      <div className="mt-4 max-w-xs">
        <label htmlFor="encuesta" className="block text-sm font-medium">
          Encuesta
        </label>
        <select
          id="encuesta"
          value={survey}
          onChange={(event) => {
            createMutation.reset();
            setSurvey(event.target.value as SurveyCode);
          }}
          className="mt-1 block min-h-11 w-full rounded-lg border border-ink-muted/40 bg-surface px-3 text-base text-ink"
        >
          {SURVEYS.map((code) => (
            <option key={code} value={code}>
              {code}
            </option>
          ))}
        </select>
      </div>

      {createMutation.isError && <ErrorState message={errorMessage(createMutation.error)} />}

      {yearsQuery.isPending && (
        <p role="status" aria-live="polite" className="mt-6 text-ink-muted">
          Cargando años…
        </p>
      )}
      {yearsQuery.isError && (
        <ErrorState
          message={errorMessage(yearsQuery.error)}
          onRetry={() => void yearsQuery.refetch()}
        />
      )}

      {yearsQuery.isSuccess && (
        <>
          <div className="mt-6 overflow-x-auto">
            <table className="w-full border-collapse text-left">
              <caption className="sr-only">Años del cuestionario de {survey}</caption>
              <thead>
                <tr className="border-b border-ink-muted/30">
                  <th scope="col" className="py-2 pr-4">
                    Año
                  </th>
                  <th scope="col" className="py-2 pr-4">
                    Estado
                  </th>
                  <th scope="col" className="py-2 pr-4">
                    Versión
                  </th>
                  <th scope="col" className="py-2 pr-4">
                    Año activo
                  </th>
                </tr>
              </thead>
              <tbody>
                {years.map((item) => (
                  <tr key={item.year} className="border-b border-ink-muted/20">
                    <th scope="row" className="py-1 pr-4 font-medium">
                      <Link
                        to={`/admin/cuestionario/${survey}/${item.year}`}
                        className="inline-flex min-h-11 items-center text-primary underline"
                      >
                        {item.year}
                      </Link>
                    </th>
                    <td className="py-2 pr-4">{STATE_LABELS[item.state ?? ''] ?? item.state}</td>
                    <td className="py-2 pr-4">{item.version}</td>
                    <td className="py-2 pr-4">{item.active ? 'Sí' : 'No'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Button className="mt-6" onClick={openDialog}>
            Crear año
          </Button>
        </>
      )}

      <ConfirmDialog
        open={dialogOpen}
        title="Crear año"
        confirmLabel="Crear"
        cancelLabel="Cancelar"
        busy={createMutation.isPending}
        onCancel={() => setDialogOpen(false)}
        onConfirm={confirmCreate}
      >
        <p>
          Se copia el cuestionario del año de origen de {survey} y el año nuevo queda en borrador.
        </p>
        <div className="mt-4 grid gap-4">
          <TextField
            id="anio-nuevo"
            ref={newYearRef}
            label="Año nuevo"
            inputMode="numeric"
            value={newYear}
            onChange={(event) => setNewYear(event.target.value)}
            error={yearError(newYear, newYearValid)}
          />
          <TextField
            id="anio-origen"
            ref={fromYearRef}
            label="Año de origen"
            inputMode="numeric"
            value={fromYear}
            onChange={(event) => setFromYear(event.target.value)}
            error={yearError(fromYear, fromYearValid)}
          />
        </div>
      </ConfirmDialog>
    </section>
  );
}
