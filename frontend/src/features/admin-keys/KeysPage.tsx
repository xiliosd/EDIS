import { useEffect, useId, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ConflictNotice } from '../../components/ConflictNotice';
import { ErrorState } from '../../components/ErrorState';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { ApiError } from '../../services/api-error';
import { errorMessage } from '../admin-questions/tree-editing';
import { formatCount } from '../admin-reports/format-number';
import { fetchReportYears, REPORT_YEARS_QUERY_KEY } from '../admin-reports/reports-api';
import {
  downloadKeysExcel,
  fetchKeysSummary,
  generateKeys,
  KEYS_QUERY_ROOT,
  keysSummaryQueryKey,
} from './keys-api';
import { ScrollableRegion } from '../../components/ScrollableRegion';

const CONFLICT_MESSAGE =
  'El número de funcionarios sin clave cambió desde que abrió la confirmación. ' +
  'Revise el resumen actualizado y vuelva a intentarlo.';

function generatedMessage(count: number): string {
  return `Se generaron ${formatCount(count)} ${count === 1 ? 'clave' : 'claves'}.`;
}

export function KeysPage() {
  useDocumentTitle('Claves de la muestra');
  const queryClient = useQueryClient();
  const yearId = useId();
  const explanationId = useId();
  const [year, setYear] = useState<number | undefined>();
  const [confirming, setConfirming] = useState(false);
  const [confirmedCount, setConfirmedCount] = useState(0);
  const [announcement, setAnnouncement] = useState('');
  const [conflict, setConflict] = useState(false);
  const statusRef = useRef<HTMLDivElement>(null);

  const years = useQuery({ queryKey: REPORT_YEARS_QUERY_KEY, queryFn: fetchReportYears });
  const summary = useQuery({
    queryKey: keysSummaryQueryKey(year),
    queryFn: () => fetchKeysSummary(year),
  });
  // Se envía el año que muestra la pantalla (el del resumen), no el que el servidor suponga.
  const summaryYear = summary.data?.year ?? year;
  const generate = useMutation({
    mutationFn: (expected: number) => generateKeys(summaryYear, expected),
  });
  const download = useMutation({
    mutationFn: (department: number) => downloadKeysExcel(summaryYear, department),
  });

  // Tras generar, el botón puede quedar deshabilitado: el foco pasa al anuncio y no se pierde.
  useEffect(() => {
    if (announcement) {
      statusRef.current?.focus();
    }
  }, [announcement]);

  const departments = summary.data?.departments ?? [];
  const totals = departments.reduce<{ eligible: number; withKey: number; withoutKey: number }>(
    (sum, department) => ({
      eligible: sum.eligible + (department.eligible ?? 0),
      withKey: sum.withKey + (department.withKey ?? 0),
      withoutKey: sum.withoutKey + (department.withoutKey ?? 0),
    }),
    { eligible: 0, withKey: 0, withoutKey: 0 },
  );
  const yearOptions = [
    ...new Set([...(years.data ?? []), ...(summaryYear ? [summaryYear] : [])]),
  ].sort((first, second) => second - first);
  const noPending = summary.isSuccess && totals.withoutKey === 0;

  function reloadSummary() {
    setConflict(false);
    void queryClient.invalidateQueries({ queryKey: KEYS_QUERY_ROOT });
  }

  function onConfirm() {
    generate.mutate(confirmedCount, {
      onSuccess: async (generated) => {
        setConfirming(false);
        setConflict(false);
        await queryClient.invalidateQueries({ queryKey: KEYS_QUERY_ROOT });
        setAnnouncement(generatedMessage(generated));
      },
      onError: (caught) => {
        setConfirming(false);
        if (caught instanceof ApiError && caught.status === 409) {
          setConflict(true);
          void queryClient.invalidateQueries({ queryKey: KEYS_QUERY_ROOT });
        }
      },
    });
  }

  function onYearChange(value: string) {
    setYear(Number(value));
    setAnnouncement('');
    setConflict(false);
    generate.reset();
  }

  return (
    <section aria-labelledby="titulo-claves">
      <h1 id="titulo-claves" className="text-2xl font-semibold">
        Claves de la muestra
      </h1>
      <p className="mt-2 text-ink-muted">
        Genere usuario y clave para los funcionarios web que aún no tienen. Las claves no se
        muestran en pantalla: se entregan solo en el Excel de cada departamento.
      </p>

      <div className="mt-4 grid gap-1">
        <label htmlFor={yearId} className="text-sm font-medium">
          Año
        </label>
        <select
          id={yearId}
          className="min-h-11 w-40 rounded-lg border border-ink-muted bg-surface px-3"
          value={summaryYear ?? ''}
          onChange={(event) => onYearChange(event.target.value)}
        >
          {yearOptions.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>

      <div
        ref={statusRef}
        role="status"
        aria-live="polite"
        tabIndex={-1}
        className="mt-4 text-ink empty:hidden"
      >
        {announcement}
      </div>

      {conflict && <ConflictNotice message={CONFLICT_MESSAGE} onReload={reloadSummary} />}
      {generate.isError && !conflict && <ErrorState message={errorMessage(generate.error)} />}
      {download.isError && <ErrorState message={errorMessage(download.error)} />}

      {summary.isPending && (
        <p role="status" aria-live="polite" className="mt-6 text-ink-muted">
          Cargando resumen…
        </p>
      )}
      {summary.isError && (
        <ErrorState message={errorMessage(summary.error)} onRetry={() => void summary.refetch()} />
      )}

      {summary.isSuccess && (
        <>
          <ScrollableRegion label="Claves de la muestra por departamento" className="mt-6">
            <table className="w-full min-w-[34rem] border-collapse text-sm">
              <caption className="sr-only">Claves de la muestra por departamento</caption>
              <thead className="bg-primary/10">
                <tr>
                  <th scope="col" className="px-3 py-2 text-left font-semibold">
                    Departamento
                  </th>
                  <th scope="col" className="px-3 py-2 text-right font-semibold">
                    Elegibles
                  </th>
                  <th scope="col" className="px-3 py-2 text-right font-semibold">
                    Con clave
                  </th>
                  <th scope="col" className="px-3 py-2 text-right font-semibold">
                    Sin clave
                  </th>
                  <th scope="col" className="px-3 py-2 text-left font-semibold">
                    Archivo
                  </th>
                </tr>
              </thead>
              <tbody>
                {departments.map((department) => (
                  <tr key={department.code} className="border-t border-ink-muted/20">
                    <th scope="row" className="px-3 py-2 text-left font-normal">
                      {department.name}
                    </th>
                    <td className="px-3 py-2 text-right">{formatCount(department.eligible)}</td>
                    <td className="px-3 py-2 text-right">{formatCount(department.withKey)}</td>
                    <td className="px-3 py-2 text-right">{formatCount(department.withoutKey)}</td>
                    <td className="px-3 py-2">
                      <Button
                        variant="secondary"
                        disabled={download.isPending}
                        aria-label={`Descargar Excel de claves de ${department.name}`}
                        onClick={() =>
                          department.code !== undefined && download.mutate(department.code)
                        }
                      >
                        Descargar
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t-2 border-ink-muted/40 font-semibold">
                  <th scope="row" className="px-3 py-2 text-left">
                    Total
                  </th>
                  <td className="px-3 py-2 text-right">{formatCount(totals.eligible)}</td>
                  <td className="px-3 py-2 text-right">{formatCount(totals.withKey)}</td>
                  <td className="px-3 py-2 text-right">{formatCount(totals.withoutKey)}</td>
                  <td className="px-3 py-2" />
                </tr>
              </tfoot>
            </table>
          </ScrollableRegion>

          <div className="mt-6">
            <Button
              disabled={noPending || generate.isPending}
              aria-describedby={noPending ? explanationId : undefined}
              onClick={() => {
                setConfirmedCount(totals.withoutKey);
                generate.reset();
                setConflict(false);
                setConfirming(true);
              }}
            >
              Generar claves
            </Button>
            {noPending && (
              <p id={explanationId} className="mt-2 text-ink-muted">
                No hay funcionarios web sin clave en este año: no hay nada que generar.
              </p>
            )}
          </div>
        </>
      )}

      <ConfirmDialog
        open={confirming}
        title="Generar claves"
        confirmLabel="Generar"
        cancelLabel="Cancelar"
        busy={generate.isPending}
        onConfirm={onConfirm}
        onCancel={() => setConfirming(false)}
      >
        <p>
          Se generarán {formatCount(confirmedCount)} claves para los funcionarios web sin clave del
          año {summaryYear}. Las claves existentes no cambian.
        </p>
      </ConfirmDialog>
    </section>
  );
}
