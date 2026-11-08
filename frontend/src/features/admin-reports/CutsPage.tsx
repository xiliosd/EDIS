import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ErrorState } from '../../components/ErrorState';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { errorMessage } from '../admin-questions/tree-editing';
import { ReportFilters } from './ReportFilters';
import {
  createCut,
  cutsQueryKey,
  downloadCutExcel,
  fetchCuts,
  REPORT_SURVEYS,
  type CutSummary,
  type ReportFilterValue,
} from './reports-api';
import { ScrollableRegion } from '../../components/ScrollableRegion';

const BOGOTA = 'America/Bogota';

function formatCutDate(iso: string | undefined): string {
  return iso ? new Date(iso).toLocaleDateString('es-CO', { timeZone: BOGOTA }) : '';
}

function formatCutTime(iso: string | undefined): string {
  // hourCycle h23 y no hour12: false, que en algunos navegadores muestra la medianoche como 24.
  return iso
    ? new Date(iso).toLocaleTimeString('es-CO', { timeZone: BOGOTA, hourCycle: 'h23' })
    : '';
}

function surveyLabel(survey: CutSummary['survey']): string {
  return REPORT_SURVEYS.find((option) => option.value === survey)?.label ?? '';
}

interface CutRowProps {
  cut: CutSummary;
  onDownload: (id: string) => void;
  disabled: boolean;
}

function CutRow({ cut, onDownload, disabled }: CutRowProps) {
  const description = `${cut.departmentName ?? ''} ${surveyLabel(cut.survey)} ${cut.year ?? ''}`;
  return (
    <tr className="border-t border-ink-muted/20">
      <td className="px-3 py-2">{formatCutDate(cut.generatedAt)}</td>
      <td className="px-3 py-2">{formatCutTime(cut.generatedAt)}</td>
      <td className="px-3 py-2">{cut.generatedBy}</td>
      <td className="px-3 py-2">{cut.departmentName}</td>
      <td className="px-3 py-2">{surveyLabel(cut.survey)}</td>
      <td className="px-3 py-2">{cut.year}</td>
      <td className="px-3 py-2">
        <Button
          variant="secondary"
          disabled={disabled}
          aria-label={`Descargar corte de ${description.trim()} del ${formatCutDate(cut.generatedAt)} ${formatCutTime(cut.generatedAt)}`}
          onClick={() => cut.id && onDownload(cut.id)}
        >
          Descargar
        </Button>
      </td>
    </tr>
  );
}

export function CutsPage() {
  useDocumentTitle('Cortes e histórico');
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState<ReportFilterValue>({ survey: 'GENERAL' });
  const [missingDepartment, setMissingDepartment] = useState(false);
  const cuts = useQuery({
    queryKey: cutsQueryKey(filters.year, filters.department),
    queryFn: () => fetchCuts(filters.year, filters.department),
  });
  const generate = useMutation({
    mutationFn: (department: number) => createCut(filters.survey, department, filters.year),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'reports', 'cuts'] }),
  });
  const download = useMutation({ mutationFn: downloadCutExcel });

  function onFiltersChange(next: ReportFilterValue) {
    setFilters(next);
    if (next.department !== undefined) {
      setMissingDepartment(false);
    }
  }

  function onGenerate() {
    if (filters.department === undefined) {
      setMissingDepartment(true);
      return;
    }
    generate.mutate(filters.department);
  }

  const rows = cuts.data ?? [];

  return (
    <section aria-labelledby="titulo-cortes">
      <h1 id="titulo-cortes" className="text-2xl font-semibold">
        Cortes e histórico
      </h1>
      <p className="mt-2 text-ink-muted">
        Un corte guarda la cobertura de un departamento en este momento para consultarla o
        descargarla después.
      </p>

      <ReportFilters survey department="required" value={filters} onChange={onFiltersChange} />

      <div className="mt-4">
        <Button disabled={generate.isPending} onClick={onGenerate}>
          {generate.isPending ? 'Generando…' : 'Generar corte'}
        </Button>
        {missingDepartment && (
          <p role="alert" className="mt-2 text-danger">
            Seleccione un departamento para generar el corte.
          </p>
        )}
      </div>
      {generate.isError && <ErrorState message={errorMessage(generate.error)} />}
      {download.isError && <ErrorState message={errorMessage(download.error)} />}

      <section aria-labelledby="titulo-historico" className="mt-8">
        <h2 id="titulo-historico" className="text-xl font-semibold">
          Histórico de cortes
        </h2>
        <div aria-live="polite">
          {cuts.isPending && <p className="mt-3 text-ink-muted">Cargando cortes…</p>}
        </div>
        {cuts.isError && (
          <ErrorState message={errorMessage(cuts.error)} onRetry={() => void cuts.refetch()} />
        )}
        {cuts.data &&
          (rows.length === 0 ? (
            <p className="mt-3 text-ink-muted">Todavía no hay cortes para estos filtros.</p>
          ) : (
            <ScrollableRegion label="Cortes generados" className="mt-3">
              <table className="w-full min-w-[44rem] border-collapse text-sm">
                <caption className="sr-only">Cortes generados</caption>
                <thead className="bg-primary/10">
                  <tr>
                    {[
                      'Fecha',
                      'Hora',
                      'Generado por',
                      'Departamento',
                      'Encuesta',
                      'Año',
                      'Archivo',
                    ].map((column) => (
                      <th key={column} scope="col" className="px-3 py-2 text-left font-semibold">
                        {column}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((cut) => (
                    <CutRow
                      key={cut.id}
                      cut={cut}
                      disabled={download.isPending}
                      onDownload={(id) => download.mutate(id)}
                    />
                  ))}
                </tbody>
              </table>
            </ScrollableRegion>
          ))}
      </section>
    </section>
  );
}
