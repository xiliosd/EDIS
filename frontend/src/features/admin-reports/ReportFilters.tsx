import { useEffect, useId } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ErrorState } from '../../components/ErrorState';
import { useOperationScope } from '../admin/operation-scope';
import { errorMessage } from '../admin-questions/tree-editing';
import {
  fetchReportYears,
  REPORT_SURVEYS,
  REPORT_YEARS_QUERY_KEY,
  type ReportFilterValue,
  type ReportSurvey,
} from './reports-api';

interface ReportFiltersProps {
  /** Muestra el selector de encuesta (GENERAL, EDI, EDID). */
  survey?: boolean;
  /** `required`: hay que elegir un departamento; `optional`: también se puede ver todo el alcance. */
  department: 'required' | 'optional';
  value: ReportFilterValue;
  onChange: (value: ReportFilterValue) => void;
}

const SELECT_CLASSES = 'min-h-11 rounded-lg border border-ink-muted bg-surface px-3';

/**
 * Filtros comunes de los reportes: año (de los que hay en la muestra) y departamento limitado al
 * alcance del usuario. Sin año elegido se usa el de la encuesta activa.
 */
export function ReportFilters({ survey = false, department, value, onChange }: ReportFiltersProps) {
  const surveyId = useId();
  const yearId = useId();
  const departmentId = useId();
  const scope = useOperationScope();
  const years = useQuery({ queryKey: REPORT_YEARS_QUERY_KEY, queryFn: fetchReportYears });

  const departments = scope.data?.departments ?? [];
  const onlyDepartment = departments.length === 1 ? departments[0].code : undefined;
  const mustChoose = department === 'required';

  // Con un solo departamento posible, elegirlo es innecesario cuando es obligatorio.
  useEffect(() => {
    if (mustChoose && value.department === undefined && onlyDepartment !== undefined) {
      onChange({ ...value, department: onlyDepartment });
    }
  }, [mustChoose, onlyDepartment, value, onChange]);

  if (scope.isError) {
    return <ErrorState message={errorMessage(scope.error)} onRetry={() => void scope.refetch()} />;
  }
  if (scope.isPending) {
    return (
      <p role="status" aria-live="polite" className="mt-4 text-ink-muted">
        Cargando filtros…
      </p>
    );
  }

  const currentYear = value.year ?? scope.data.defaultYear;
  const yearOptions = [
    ...new Set([...(years.data ?? []), ...(currentYear ? [currentYear] : [])]),
  ].sort((first, second) => second - first);
  const allLabel = scope.data.allDepartments
    ? 'Todos los departamentos'
    : 'Todos mis departamentos';

  return (
    <form
      aria-label="Filtros del reporte"
      className="mt-4 flex flex-wrap items-end gap-4"
      onSubmit={(event) => event.preventDefault()}
    >
      {survey && (
        <div className="grid gap-1">
          <label htmlFor={surveyId} className="text-sm font-medium">
            Encuesta
          </label>
          <select
            id={surveyId}
            className={SELECT_CLASSES}
            value={value.survey}
            onChange={(event) => onChange({ ...value, survey: event.target.value as ReportSurvey })}
          >
            {REPORT_SURVEYS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      )}
      <div className="grid gap-1">
        <label htmlFor={yearId} className="text-sm font-medium">
          Año
        </label>
        <select
          id={yearId}
          className={SELECT_CLASSES}
          value={currentYear ?? ''}
          onChange={(event) => onChange({ ...value, year: Number(event.target.value) })}
        >
          {yearOptions.map((year) => (
            <option key={year} value={year}>
              {year}
            </option>
          ))}
        </select>
      </div>
      <div className="grid gap-1">
        <label htmlFor={departmentId} className="text-sm font-medium">
          Departamento
        </label>
        <select
          id={departmentId}
          className={SELECT_CLASSES}
          required={mustChoose}
          value={value.department ?? ''}
          onChange={(event) =>
            onChange({
              ...value,
              department: event.target.value === '' ? undefined : Number(event.target.value),
            })
          }
        >
          <option value="">{mustChoose ? 'Seleccione un departamento' : allLabel}</option>
          {departments.map((option) => (
            <option key={option.code} value={option.code}>
              {option.name}
            </option>
          ))}
        </select>
      </div>
    </form>
  );
}
