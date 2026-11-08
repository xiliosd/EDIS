import { useId, useState } from 'react';
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ErrorState } from '../../components/ErrorState';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { errorMessage } from '../admin-questions/tree-editing';
import { formatCount } from './format-number';
import { ReportFilters } from './ReportFilters';
import { ReportPager } from './ReportPager';
import {
  downloadOperationalStatusExcel,
  fetchOperationalStatus,
  operationalStatusQueryKey,
  REPORT_PAGE_SIZE,
  type OperationalStatusFilter,
  type OperationalStatusFilters,
  type PersonStatusRow,
} from './reports-api';

const STATUS_OPTIONS: readonly { value: OperationalStatusFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'COMPLETE', label: 'Completos' },
  { value: 'INCOMPLETE', label: 'Incompletos' },
  { value: 'NOT_STARTED', label: 'Sin diligenciar' },
  { value: 'NOVELTY', label: 'Novedad' },
];

const STATUS_LABELS: Record<NonNullable<PersonStatusRow['status']>, string> = {
  COMPLETE: 'Completo',
  NOVELTY: 'Novedad (SD)',
  INCOMPLETE: 'Incompleto',
  NOT_STARTED: 'Sin diligenciar',
};

const COLUMNS = [
  'Entidad',
  'Nivel',
  'Cédula',
  'Funcionario',
  'Estado',
  'Novedad',
  'Capítulos faltantes',
];

function noveltyText(row: PersonStatusRow): string {
  const parts = [row.noveltyName, row.otherCause].filter(Boolean);
  return parts.length > 0 ? parts.join(': ') : '—';
}

export function OperationalStatusPage() {
  useDocumentTitle('Estado del operativo');
  const entityId = useId();
  const statusId = useId();
  const [filters, setFilters] = useState<OperationalStatusFilters>({
    survey: 'GENERAL',
    status: 'ALL',
  });
  const [page, setPage] = useState(0);
  const hasDepartment = filters.department !== undefined;
  const result = useQuery({
    queryKey: operationalStatusQueryKey(filters, page),
    queryFn: () => fetchOperationalStatus(filters, page),
    enabled: hasDepartment,
    placeholderData: hasDepartment ? keepPreviousData : undefined,
  });
  const download = useMutation({ mutationFn: () => downloadOperationalStatusExcel(filters) });

  function change(next: OperationalStatusFilters) {
    setFilters(next);
    setPage(0);
  }

  const rows = result.data?.rows ?? [];
  const total = result.data?.total ?? 0;
  const pageCount = Math.ceil(total / REPORT_PAGE_SIZE);

  return (
    <section aria-labelledby="titulo-estado">
      <h1 id="titulo-estado" className="text-2xl font-semibold">
        Estado del operativo
      </h1>
      <p className="mt-2 text-ink-muted">
        Funcionarios del departamento con su estado de diligenciamiento.
      </p>

      <ReportFilters
        survey
        department="required"
        value={filters}
        onChange={(next) => change({ ...filters, ...next })}
      />
      <div className="mt-4 flex flex-wrap items-end gap-4">
        <div className="grid gap-1">
          <label htmlFor={statusId} className="text-sm font-medium">
            Estado
          </label>
          <select
            id={statusId}
            className="min-h-11 rounded-lg border border-ink-muted bg-surface px-3"
            value={filters.status}
            onChange={(event) =>
              change({ ...filters, status: event.target.value as OperationalStatusFilter })
            }
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="grid gap-1">
          <label htmlFor={entityId} className="text-sm font-medium">
            Código de entidad
          </label>
          <input
            id={entityId}
            type="text"
            inputMode="numeric"
            className="min-h-11 w-40 rounded-lg border border-ink-muted bg-surface px-3"
            value={filters.entity ?? ''}
            onChange={(event) => {
              const digits = event.target.value.replace(/\D/g, '');
              change({ ...filters, entity: digits === '' ? undefined : Number(digits) });
            }}
          />
        </div>
        <Button
          variant="secondary"
          disabled={!hasDepartment || download.isPending}
          onClick={() => download.mutate()}
        >
          {download.isPending ? 'Exportando…' : 'Exportar a Excel'}
        </Button>
      </div>
      {download.isError && <ErrorState message={errorMessage(download.error)} />}

      {!hasDepartment && (
        <p className="mt-6 text-ink-muted">Seleccione un departamento para ver el estado.</p>
      )}
      <div aria-live="polite">
        {hasDepartment && result.isPending && (
          <p className="mt-6 text-ink-muted">Cargando estado…</p>
        )}
      </div>
      {hasDepartment && result.isError && (
        <ErrorState message={errorMessage(result.error)} onRetry={() => void result.refetch()} />
      )}
      {hasDepartment &&
        result.data &&
        (rows.length === 0 ? (
          <p className="mt-6 text-ink-muted">No hay funcionarios para estos filtros.</p>
        ) : (
          <>
            <div
              className={`mt-6 overflow-x-auto ${result.isPlaceholderData ? 'opacity-60' : ''}`}
              aria-busy={result.isPlaceholderData}
            >
              <table className="w-full min-w-[56rem] border-collapse text-sm">
                <caption className="sr-only">
                  Estado de los funcionarios, {formatCount(total)} en total
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
                  {rows.map((row, index) => (
                    <tr
                      key={`${row.respondentCode}-${row.loadType}-${index}`}
                      className="border-t border-ink-muted/20"
                    >
                      <td className="px-3 py-2">{row.entityName}</td>
                      <td className="px-3 py-2">{row.level}</td>
                      <td className="px-3 py-2">{row.respondentCode}</td>
                      <td className="px-3 py-2">{row.name}</td>
                      <td className="px-3 py-2 font-medium">
                        {row.status ? STATUS_LABELS[row.status] : ''}
                      </td>
                      <td className="px-3 py-2">{noveltyText(row)}</td>
                      <td className="px-3 py-2">{row.missingChapters?.join(', ') || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <ReportPager page={page} pageCount={pageCount} total={total} onChange={setPage} />
          </>
        ))}
    </section>
  );
}
