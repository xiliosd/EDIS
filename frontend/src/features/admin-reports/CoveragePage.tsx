import { useId, useRef, useState, type KeyboardEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ErrorState } from '../../components/ErrorState';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { errorMessage } from '../admin-questions/tree-editing';
import { CoverageTable } from './CoverageTable';
import { ReportFilters } from './ReportFilters';
import {
  coverageQueryKey,
  downloadCoverageExcel,
  fetchCoverage,
  REPORT_SURVEYS,
  type ReportFilterValue,
  type ReportSurvey,
} from './reports-api';

const TAB_CLASSES =
  'inline-flex min-h-11 items-center rounded-lg px-4 font-medium text-ink hover:bg-primary/10';
const ACTIVE_TAB_CLASSES = 'bg-primary/10 text-primary underline';

interface SurveyTabsProps {
  value: ReportSurvey;
  panelId: string;
  onChange: (survey: ReportSurvey) => void;
}

/** Pestañas General / EDI / EDID con flechas de teclado (patrón de pestañas de WAI-ARIA). */
function SurveyTabs({ value, panelId, onChange }: SurveyTabsProps) {
  const tabs = useRef<Record<string, HTMLButtonElement | null>>({});

  function moveWithArrows(event: KeyboardEvent, index: number) {
    const offsets: Record<string, number> = { ArrowRight: 1, ArrowLeft: -1 };
    const offset = offsets[event.key];
    if (offset === undefined) {
      return;
    }
    event.preventDefault();
    const next = REPORT_SURVEYS[(index + offset + REPORT_SURVEYS.length) % REPORT_SURVEYS.length];
    onChange(next.value);
    tabs.current[next.value]?.focus();
  }

  return (
    <div role="tablist" aria-label="Encuesta" className="mt-4 flex flex-wrap gap-1">
      {REPORT_SURVEYS.map((survey, index) => {
        const selected = survey.value === value;
        return (
          <button
            key={survey.value}
            ref={(element) => {
              tabs.current[survey.value] = element;
            }}
            type="button"
            role="tab"
            id={`${panelId}-tab-${survey.value}`}
            aria-selected={selected}
            aria-controls={panelId}
            tabIndex={selected ? 0 : -1}
            className={`${TAB_CLASSES} ${selected ? ACTIVE_TAB_CLASSES : ''}`}
            onClick={() => onChange(survey.value)}
            onKeyDown={(event) => moveWithArrows(event, index)}
          >
            {survey.label}
          </button>
        );
      })}
    </div>
  );
}

function formatGeneratedAt(iso: string | undefined): string {
  return iso ? new Date(iso).toLocaleString('es-CO', { timeZone: 'America/Bogota' }) : '';
}

export function CoveragePage() {
  useDocumentTitle('Cobertura');
  const panelId = useId();
  const [filters, setFilters] = useState<ReportFilterValue>({ survey: 'GENERAL' });
  const coverage = useQuery({
    queryKey: coverageQueryKey(filters),
    queryFn: () => fetchCoverage(filters),
  });
  const exportExcel = useMutation({ mutationFn: () => downloadCoverageExcel(filters) });
  const report = coverage.data;
  const surveyLabel = REPORT_SURVEYS.find((survey) => survey.value === filters.survey)?.label;

  return (
    <section aria-labelledby="titulo-cobertura">
      <h1 id="titulo-cobertura" className="text-2xl font-semibold">
        Cobertura
      </h1>
      <p className="mt-2 text-ink-muted">
        Avance de la recolección por departamento y entidad según la muestra del operativo.
      </p>

      <SurveyTabs
        value={filters.survey}
        panelId={panelId}
        onChange={(survey) => setFilters((current) => ({ ...current, survey }))}
      />
      <ReportFilters department="optional" value={filters} onChange={setFilters} />

      <div
        role="tabpanel"
        id={panelId}
        aria-labelledby={`${panelId}-tab-${filters.survey}`}
        className="mt-4"
      >
        <div className="flex flex-wrap items-center gap-3">
          <Button
            variant="secondary"
            disabled={!report || exportExcel.isPending}
            onClick={() => exportExcel.mutate()}
          >
            Exportar a Excel
          </Button>
          {report?.generatedAt && (
            <p className="text-sm text-ink-muted">
              Generado el {formatGeneratedAt(report.generatedAt)}
            </p>
          )}
        </div>
        {exportExcel.isError && <ErrorState message={errorMessage(exportExcel.error)} />}

        <div aria-live="polite">
          {coverage.isPending && <p className="mt-4 text-ink-muted">Cargando cobertura…</p>}
        </div>
        {coverage.isError && (
          <ErrorState
            message={errorMessage(coverage.error)}
            onRetry={() => void coverage.refetch()}
          />
        )}
        {report &&
          ((report.departments ?? []).length === 0 ? (
            <p className="mt-4 text-ink-muted">No hay muestra para los filtros elegidos.</p>
          ) : (
            <CoverageTable
              report={report}
              caption={`Cobertura ${surveyLabel} ${report.year ?? ''} por departamento y entidad`}
            />
          ))}
      </div>
    </section>
  );
}
