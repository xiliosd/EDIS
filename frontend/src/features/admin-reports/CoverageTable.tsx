import { Fragment, useState } from 'react';
import { formatCount, formatPercent } from './format-number';
import type {
  CoverageReport,
  CoverageTotals,
  DepartmentCoverage,
  EntityCoverage,
  LevelCoverage,
} from './reports-api';
import { ScrollableRegion } from '../../components/ScrollableRegion';

const HEADER_CELL = 'px-3 py-2 text-right font-semibold';
const NUMBER_CELL = 'whitespace-nowrap px-3 py-2 text-right tabular-nums';
const COLUMN_COUNT = 10;

const NUMERIC_COLUMNS = [
  'Muestra',
  'Muestra efectiva',
  'Novedades',
  'Completas',
  '% completas',
  'Incompletas',
  '% incompletas',
  'Sin iniciar',
  '% sin iniciar',
];

function TotalsHead({ firstColumn }: { firstColumn: string }) {
  return (
    <thead className="bg-primary/10">
      <tr>
        <th scope="col" className="px-3 py-2 text-left font-semibold">
          {firstColumn}
        </th>
        {NUMERIC_COLUMNS.map((column) => (
          <th key={column} scope="col" className={HEADER_CELL}>
            {column}
          </th>
        ))}
      </tr>
    </thead>
  );
}

function TotalsCells({ totals }: { totals: CoverageTotals | undefined }) {
  return (
    <>
      <td className={NUMBER_CELL}>{formatCount(totals?.sample)}</td>
      <td className={NUMBER_CELL}>{formatCount(totals?.effectiveSample)}</td>
      <td className={NUMBER_CELL}>{formatCount(totals?.novelties)}</td>
      <td className={NUMBER_CELL}>{formatCount(totals?.complete)}</td>
      <td className={NUMBER_CELL}>{formatPercent(totals?.completePct)}</td>
      <td className={NUMBER_CELL}>{formatCount(totals?.incomplete)}</td>
      <td className={NUMBER_CELL}>{formatPercent(totals?.incompletePct)}</td>
      <td className={NUMBER_CELL}>{formatCount(totals?.notStarted)}</td>
      <td className={NUMBER_CELL}>{formatPercent(totals?.notStartedPct)}</td>
    </>
  );
}

function LevelRow({ level }: { level: LevelCoverage }) {
  return (
    <tr className="bg-surface text-ink-muted">
      <th scope="row" className="py-2 pl-10 pr-3 text-left font-normal">
        Nivel {level.level} (carga web)
      </th>
      <TotalsCells totals={level.totals} />
    </tr>
  );
}

function EntityRow({ entity }: { entity: EntityCoverage }) {
  const [expanded, setExpanded] = useState(false);
  const levels = entity.webLevels ?? [];
  return (
    <Fragment>
      <tr className="border-t border-ink-muted/20">
        <th scope="row" className="px-3 py-2 text-left font-normal">
          {levels.length > 0 ? (
            <button
              type="button"
              aria-expanded={expanded}
              onClick={() => setExpanded((current) => !current)}
              className="inline-flex min-h-11 items-center gap-2 rounded-lg text-left text-primary underline"
            >
              <span aria-hidden="true">{expanded ? '▾' : '▸'}</span>
              <span>{entity.name}</span>
            </button>
          ) : (
            <span>{entity.name}</span>
          )}
        </th>
        <TotalsCells totals={entity.totals} />
      </tr>
      {expanded && levels.map((level) => <LevelRow key={level.level} level={level} />)}
    </Fragment>
  );
}

function DepartmentRows({ department }: { department: DepartmentCoverage }) {
  return (
    <tbody>
      <tr className="bg-primary/5">
        <th scope="rowgroup" colSpan={COLUMN_COUNT} className="px-3 py-2 text-left font-semibold">
          {department.name}
        </th>
      </tr>
      {(department.entities ?? []).map((entity) => (
        <EntityRow key={entity.code} entity={entity} />
      ))}
      <tr className="border-t-2 border-ink-muted/40 font-semibold">
        <th scope="row" className="px-3 py-2 text-left">
          Total {department.name}
        </th>
        <TotalsCells totals={department.totals} />
      </tr>
    </tbody>
  );
}

/** Rotulado según el alcance (Ruling 5): «nacional» solo si el reporte abarca todos los departamentos. */
export function consolidatedLabels(coversAllDepartments: boolean | undefined) {
  return coversAllDepartments
    ? { block: 'Consolidado nacional', total: 'Total nacional' }
    : { block: 'Consolidado del alcance', total: 'Total del alcance consultado' };
}

interface CoverageTableProps {
  report: CoverageReport;
  caption: string;
}

/** Cobertura por departamento y entidad (con niveles web desplegables) y su consolidado. */
export function CoverageTable({ report, caption }: CoverageTableProps) {
  const labels = consolidatedLabels(report.coversAllDepartments);
  const departments = report.departments ?? [];
  return (
    <>
      <ScrollableRegion label={caption} className="mt-4">
        <table className="w-full min-w-[60rem] border-collapse text-sm">
          <caption className="pb-2 text-left font-semibold">{caption}</caption>
          <TotalsHead firstColumn="Departamento y entidad" />
          {departments.map((department) => (
            <DepartmentRows key={department.code} department={department} />
          ))}
        </table>
      </ScrollableRegion>

      <section aria-labelledby="titulo-consolidado" className="mt-8">
        <h2 id="titulo-consolidado" className="text-xl font-semibold">
          {labels.block}
        </h2>
        <p className="mt-1 text-ink-muted">
          {formatCount(report.departmentCount)} departamentos y {formatCount(report.entityCount)}{' '}
          entidades.
        </p>
        <ScrollableRegion label={`Tabla del ${labels.block.toLowerCase()}`} className="mt-3">
          <table className="w-full min-w-[60rem] border-collapse text-sm">
            <caption className="sr-only">{labels.block}</caption>
            <TotalsHead firstColumn="Concepto" />
            <tbody>
              <tr className="font-semibold">
                <th scope="row" className="px-3 py-2 text-left">
                  {labels.total}
                </th>
                <TotalsCells totals={report.national} />
              </tr>
              {(report.nationalWebLevels ?? []).map((level) => (
                <LevelRow key={level.level} level={level} />
              ))}
            </tbody>
          </table>
        </ScrollableRegion>
      </section>
    </>
  );
}
