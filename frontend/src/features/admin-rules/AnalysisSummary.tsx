import { useEffect, useId, useRef, type MouseEvent } from 'react';
import { Link } from 'react-router';
import type { AnalysisReport, Finding } from './rules-api';

/** Encabezado en español de cada código de hallazgo del analizador. */
export const FINDING_HEADINGS: Record<string, string> = {
  ESTRUCTURA: 'Error de estructura',
  PREGUNTA_INEXISTENTE: 'Pregunta inexistente o inactiva',
  CICLO: 'Ciclo de saltos',
  VALOR_NO_ES_OPCION: 'Valor que no es una opción',
  RANGO_INVALIDO: 'Rango inválido',
  RANGO_EXCEDE_COLUMNA: 'Rango mayor que la columna',
  VALIDACION_EN_OPCION: 'Validación de rango en una pregunta de opciones',
  NUNCA_SE_MUESTRA: 'Pregunta que nunca se muestra',
  NUNCA_SE_CUMPLE: 'Regla que nunca se cumple',
  NO_ANALIZABLE: 'No se pudo analizar',
  DESTINO_ANTES: 'Destino antes del disparador',
  REGLAS_SOLAPADAS: 'Reglas solapadas',
  PENDIENTE_COLUMNA: 'Pregunta pendiente de columna',
};

export function findingHeading(code: string | undefined): string {
  return (code && FINDING_HEADINGS[code]) || code || 'Hallazgo';
}

export type Severity = 'error' | 'warning';

export interface LocatedFinding extends Finding {
  severity: Severity;
}

/** Hallazgos del informe cuyo elemento es alguno de los dados (p. ej. `regla:c01a-no`). */
export function findingsOfElements(
  report: AnalysisReport | undefined,
  elements: string[],
): LocatedFinding[] {
  const wanted = new Set(elements);
  return [
    ...(report?.errors ?? []).map((item): LocatedFinding => ({ ...item, severity: 'error' })),
    ...(report?.warnings ?? []).map((item): LocatedFinding => ({ ...item, severity: 'warning' })),
  ].filter((item) => wanted.has(item.element ?? ''));
}

/** Lista breve de hallazgos junto al elemento que los causa. */
export function FindingNotes({ findings }: { findings: LocatedFinding[] }) {
  if (findings.length === 0) {
    return null;
  }
  return (
    <ul className="mt-2 grid gap-1 text-sm">
      {findings.map((item, index) => (
        <li key={`${item.code}-${index}`}>
          <span className="font-medium text-ink">
            {item.severity === 'error' ? 'Error: ' : 'Advertencia: '}
          </span>
          <strong>{findingHeading(item.code)}</strong>
          {' — '}
          <span>{item.message}</span>
        </li>
      ))}
    </ul>
  );
}

interface FindingTarget {
  label: string;
  /** Enlace interno de la misma página (ancla) o ruta del simulador. */
  href: string;
  anchor: boolean;
}

function targetOf(element: string | undefined, survey: string, year: number): FindingTarget | null {
  const separator = (element ?? '').indexOf(':');
  if (separator < 0) {
    return null;
  }
  const kind = (element ?? '').slice(0, separator);
  const id = (element ?? '').slice(separator + 1);
  if (!id) {
    return null;
  }
  switch (kind) {
    case 'regla':
      return { label: `Ir a la regla ${id}`, href: `#regla-${id}`, anchor: true };
    case 'validacion':
    case 'pregunta':
      return { label: `Ir a la pregunta ${id}`, href: `#pregunta-${id}`, anchor: true };
    case 'caso':
      return {
        label: `Ir al caso ${id}`,
        href: `/admin/cuestionario/${survey}/${year}/simulador#caso-${id}`,
        anchor: false,
      };
    default:
      return null;
  }
}

/** Lleva el foco al elemento (WCAG 2.4.3); un ancla sola solo desplaza la vista. */
function focusAnchor(event: MouseEvent<HTMLAnchorElement>) {
  const target = document.getElementById(event.currentTarget.getAttribute('href')?.slice(1) ?? '');
  if (target) {
    event.preventDefault();
    target.focus();
    target.scrollIntoView?.({ block: 'center' });
  }
}

function FindingItem({
  finding,
  survey,
  year,
}: {
  finding: Finding;
  survey: string;
  year: number;
}) {
  const target = targetOf(finding.element, survey, year);
  const linkClass = 'inline-flex min-h-11 items-center text-primary underline';
  return (
    <li className="rounded-lg border border-ink-muted/30 bg-surface p-3">
      <p className="font-medium">{findingHeading(finding.code)}</p>
      <p>{finding.message}</p>
      {target &&
        (target.anchor ? (
          <a href={target.href} onClick={focusAnchor} className={linkClass}>
            {target.label}
          </a>
        ) : (
          <Link to={target.href} className={linkClass}>
            {target.label}
          </Link>
        ))}
    </li>
  );
}

interface AnalysisSummaryProps {
  report: AnalysisReport;
  survey: string;
  year: number;
  /** El servidor rechazó el guardado: solo errores, en una alerta que recibe el foco. */
  rejected?: boolean;
}

/** Hallazgos del análisis: errores primero, luego advertencias, con enlace a cada elemento. */
export function AnalysisSummary({ report, survey, year, rejected = false }: AnalysisSummaryProps) {
  const alertRef = useRef<HTMLDivElement>(null);
  const idBase = useId();
  const errors = report.errors ?? [];
  const warnings = rejected ? [] : (report.warnings ?? []);

  useEffect(() => {
    if (rejected) {
      alertRef.current?.focus();
    }
  }, [rejected, report]);

  const lists = (
    <>
      {errors.length > 0 && (
        <section aria-labelledby={`${idBase}-errores`} className="mt-3">
          <h3 id={`${idBase}-errores`} className="font-semibold text-danger">
            {rejected ? `Errores nuevos (${errors.length})` : `Errores (${errors.length})`}
          </h3>
          <ul className="mt-2 grid gap-2">
            {errors.map((item, index) => (
              <FindingItem
                key={`${item.code}-${item.element}-${index}`}
                finding={item}
                survey={survey}
                year={year}
              />
            ))}
          </ul>
        </section>
      )}
      {warnings.length > 0 && (
        <section aria-labelledby={`${idBase}-advertencias`} className="mt-3">
          <h3 id={`${idBase}-advertencias`} className="font-semibold">
            {`Advertencias (${warnings.length})`}
          </h3>
          <ul className="mt-2 grid gap-2">
            {warnings.map((item, index) => (
              <FindingItem
                key={`${item.code}-${item.element}-${index}`}
                finding={item}
                survey={survey}
                year={year}
              />
            ))}
          </ul>
        </section>
      )}
    </>
  );

  if (rejected) {
    return (
      <div
        ref={alertRef}
        role="alert"
        tabIndex={-1}
        className="mt-6 rounded-xl border border-danger/40 bg-surface p-4"
      >
        <p className="font-semibold text-danger">
          No se guardó: el cambio agrega errores al cuestionario.
        </p>
        {lists}
      </div>
    );
  }
  if (errors.length === 0 && warnings.length === 0) {
    return <p className="mt-3">El análisis no encontró problemas.</p>;
  }
  return lists;
}
