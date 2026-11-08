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
  downloadNoveltiesExcel,
  fetchNovelties,
  noveltiesQueryKey,
  REPORT_PAGE_SIZE,
  type ReportFilterValue,
} from './reports-api';

const COLUMNS = [
  'Departamento',
  'Entidad',
  'Nivel',
  'Cédula',
  'Funcionario',
  'Novedad',
  'Otra causa',
  'Completo',
];

export function NoveltyReportPage() {
  useDocumentTitle('Novedades');
  const [filters, setFilters] = useState<ReportFilterValue>({ survey: 'GENERAL' });
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: noveltiesQueryKey(filters),
    queryFn: () => fetchNovelties(filters),
  });
  const download = useMutation({ mutationFn: () => downloadNoveltiesExcel(filters) });

  const all = result.data ?? [];
  const pageCount = Math.ceil(all.length / REPORT_PAGE_SIZE);
  const visible = all.slice(page * REPORT_PAGE_SIZE, (page + 1) * REPORT_PAGE_SIZE);

  return (
    <section aria-labelledby="titulo-novedades">
      <h1 id="titulo-novedades" className="text-2xl font-semibold">
        Novedades
      </h1>
      <p className="mt-2 text-ink-muted">Funcionarios con novedad registrada.</p>

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
        {result.isPending && <p className="mt-6 text-ink-muted">Cargando novedades…</p>}
      </div>
      {result.isError && (
        <ErrorState message={errorMessage(result.error)} onRetry={() => void result.refetch()} />
      )}
      {result.data &&
        (all.length === 0 ? (
          <p className="mt-6 text-ink-muted">No hay novedades para estos filtros.</p>
        ) : (
          <>
            <div className="mt-6 overflow-x-auto">
              <table className="w-full min-w-[56rem] border-collapse text-sm">
                <caption className="sr-only">
                  Funcionarios con novedad, {formatCount(all.length)} en total
                </caption>
                <thead className="bg-primary/10">
                  <tr>
                    {COLUMNS.map((column) => (
                      <th key={column} scope="col" className="px-3 py-2 text-left font-semibold">
                        {column}
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
                      <td className="px-3 py-2">{row.departmentName}</td>
                      <td className="px-3 py-2">{row.entityName}</td>
                      <td className="px-3 py-2">{row.level}</td>
                      <td className="px-3 py-2">{row.respondentCode}</td>
                      <td className="px-3 py-2">{row.name}</td>
                      <td className="px-3 py-2">{row.noveltyName ?? row.noveltyCode ?? '—'}</td>
                      <td className="px-3 py-2">{row.otherCause || '—'}</td>
                      <td className="px-3 py-2">{row.complete ? 'Sí' : 'No'}</td>
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
