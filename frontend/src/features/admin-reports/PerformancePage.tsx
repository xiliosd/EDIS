import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ErrorState } from '../../components/ErrorState';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { errorMessage } from '../admin-questions/tree-editing';
import { formatCount } from './format-number';
import { ReportFilters } from './ReportFilters';
import { ReportPager } from './ReportPager';
import {
  downloadPerformanceExcel,
  fetchPerformance,
  performanceQueryKey,
  REPORT_PAGE_SIZE,
  type ReportFilterValue,
} from './reports-api';

const CHAPTERS = ['B', 'C', 'D', 'E', 'F', 'G', 'H'];
const NO_VALUE = '—';

/** Fecha ISO (aaaa-mm-dd) a dd/mm/aaaa sin pasar por Date, para no mover el día por zona horaria. */
function formatDate(date: string): string {
  const [year, month, day] = date.split('-');
  return `${day}/${month}/${year}`;
}

function formatSavedAt(date: string | undefined, time: string | undefined): string {
  if (!date) {
    return NO_VALUE;
  }
  return time ? `${formatDate(date)} ${time.slice(0, 8)}` : formatDate(date);
}

export function PerformancePage() {
  useDocumentTitle('Rendimientos');
  const [filters, setFilters] = useState<ReportFilterValue>({ survey: 'GENERAL' });
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: performanceQueryKey(filters),
    queryFn: () => fetchPerformance(filters),
  });
  const download = useMutation({ mutationFn: () => downloadPerformanceExcel(filters) });

  const all = result.data ?? [];
  const pageCount = Math.ceil(all.length / REPORT_PAGE_SIZE);
  const visible = all.slice(page * REPORT_PAGE_SIZE, (page + 1) * REPORT_PAGE_SIZE);

  return (
    <section aria-labelledby="titulo-rendimientos">
      <h1 id="titulo-rendimientos" className="text-2xl font-semibold">
        Rendimientos
      </h1>
      <p className="mt-2 text-ink-muted">
        Fecha y hora del último guardado de cada capítulo (B a H) de quienes iniciaron.
      </p>

      <ReportFilters
        survey
        department="optional"
        value={filters}
        onChange={(next) => {
          setFilters(next);
          setPage(0);
        }}
      />
      <div className="mt-4">
        <Button variant="secondary" disabled={download.isPending} onClick={() => download.mutate()}>
          {download.isPending ? 'Exportando…' : 'Exportar a Excel'}
        </Button>
      </div>
      {download.isError && <ErrorState message={errorMessage(download.error)} />}

      <div aria-live="polite">
        {result.isPending && <p className="mt-6 text-ink-muted">Cargando rendimientos…</p>}
      </div>
      {result.isError && (
        <ErrorState message={errorMessage(result.error)} onRetry={() => void result.refetch()} />
      )}
      {result.data &&
        (all.length === 0 ? (
          <p className="mt-6 text-ink-muted">No hay funcionarios que hayan iniciado.</p>
        ) : (
          <>
            <div className="mt-6 overflow-x-auto">
              <table className="w-full min-w-[72rem] border-collapse text-sm">
                <caption className="sr-only">
                  Rendimientos por capítulo, {formatCount(all.length)} funcionarios
                </caption>
                <thead className="bg-primary/10">
                  <tr>
                    {['Entidad', 'Nivel', 'Cédula', 'Funcionario'].map((column) => (
                      <th key={column} scope="col" className="px-3 py-2 text-left font-semibold">
                        {column}
                      </th>
                    ))}
                    {CHAPTERS.map((chapter) => (
                      <th key={chapter} scope="col" className="px-3 py-2 text-left font-semibold">
                        Capítulo {chapter}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {visible.map((row, index) => (
                    <tr
                      key={`${row.respondentCode}-${index}`}
                      className="border-t border-ink-muted/20"
                    >
                      <td className="px-3 py-2">{row.entityName}</td>
                      <td className="px-3 py-2">{row.level}</td>
                      <td className="px-3 py-2">{row.respondentCode}</td>
                      <td className="px-3 py-2">{row.name}</td>
                      {CHAPTERS.map((chapter) => {
                        const saved = row.chapters?.find((entry) => entry.chapter === chapter);
                        return (
                          <td key={chapter} className="px-3 py-2">
                            {formatSavedAt(saved?.date, saved?.time)}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <ReportPager page={page} pageCount={pageCount} total={all.length} onChange={setPage} />
          </>
        ))}
    </section>
  );
}
