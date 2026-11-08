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
import { ReportFilters } from '../admin-reports/ReportFilters';
import type { ReportFilterValue } from '../admin-reports/reports-api';
import { NoveltyEditor, type NoveltyChoice } from './NoveltyEditor';
import {
  assignNovelty,
  fetchNoveltyCatalog,
  fetchNoveltyEntities,
  fetchNoveltyPeople,
  noveltyCatalogQueryKey,
  noveltyEntitiesQueryKey,
  noveltyPeopleQueryKey,
  NOVELTIES_QUERY_ROOT,
  removeNovelty,
  type NoveltyPerson,
} from './novelties-api';

const STATUS_LABELS: Record<NonNullable<NoveltyPerson['status']>, string> = {
  COMPLETE: 'Completa',
  NOVELTY: 'Con novedad',
  INCOMPLETE: 'Incompleta',
  NOT_STARTED: 'Sin iniciar',
};

const SELECT_CLASSES = 'min-h-11 rounded-lg border border-ink-muted bg-surface px-3';

const CONFLICT_MESSAGE =
  'La novedad de este funcionario cambió mientras la editaba. Se recargó la lista: revise y repita el cambio.';

function hasNoveltyAssigned(person: NoveltyPerson): boolean {
  return person.noveltyCode !== undefined && person.noveltyCode !== null;
}

export function NoveltiesPage() {
  useDocumentTitle('Gestión de novedades');
  const queryClient = useQueryClient();
  const entityId = useId();
  const [filters, setFilters] = useState<ReportFilterValue>({ survey: 'GENERAL' });
  const [entity, setEntity] = useState<number>();
  const [editing, setEditing] = useState<NoveltyPerson | null>(null);
  const [removing, setRemoving] = useState<NoveltyPerson | null>(null);
  const [conflict, setConflict] = useState<string>();
  const [editorError, setEditorError] = useState<unknown>();
  const [removeError, setRemoveError] = useState<unknown>();
  const [focusTarget, setFocusTarget] = useState<string>();
  const rows = useRef(new Map<string, HTMLTableRowElement>());

  const { year, department } = filters;
  const catalog = useQuery({
    queryKey: noveltyCatalogQueryKey(year),
    queryFn: () => fetchNoveltyCatalog(year),
  });
  const entities = useQuery({
    queryKey: noveltyEntitiesQueryKey(year, department),
    queryFn: () => fetchNoveltyEntities(year, department as number),
    enabled: department !== undefined,
  });
  const people = useQuery({
    queryKey: noveltyPeopleQueryKey(year, department, entity),
    queryFn: () => fetchNoveltyPeople(year, department as number, entity as number),
    enabled: department !== undefined && entity !== undefined,
  });

  const assign = useMutation({ mutationFn: assignNovelty });
  const remove = useMutation({
    mutationFn: (person: NoveltyPerson) =>
      removeNovelty(year, person.respondentCode ?? '', {
        expectedNoveltyCode: person.noveltyCode,
        expectedOtherCause: person.otherCause,
      }),
  });

  // Tras cerrar un diálogo el foco vuelve a la fila de quien se editó (WCAG 2.4.3).
  useEffect(() => {
    if (focusTarget !== undefined) {
      rows.current.get(focusTarget)?.focus();
      setFocusTarget(undefined);
    }
  }, [focusTarget]);

  function onFiltersChange(next: ReportFilterValue) {
    if (next.department !== filters.department || next.year !== filters.year) {
      setEntity(undefined);
      setConflict(undefined);
    }
    setFilters(next);
  }

  function refreshLists() {
    return queryClient.invalidateQueries({ queryKey: NOVELTIES_QUERY_ROOT });
  }

  async function afterChange(respondentCode: string) {
    // El reporte de novedades muestra los mismos datos.
    void queryClient.invalidateQueries({ queryKey: ['admin', 'reports', 'novelties'] });
    await refreshLists();
    setFocusTarget(respondentCode);
  }

  async function onConflict() {
    setEditing(null);
    setRemoving(null);
    setConflict(CONFLICT_MESSAGE);
    await refreshLists();
  }

  async function save(person: NoveltyPerson, choice: NoveltyChoice) {
    const respondentCode = person.respondentCode ?? '';
    setEditorError(undefined);
    try {
      await assign.mutateAsync({
        year,
        respondentCode,
        noveltyCode: choice.noveltyCode,
        otherCause: choice.otherCause,
        expectedNoveltyCode: person.noveltyCode,
        expectedOtherCause: person.otherCause,
      });
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409) {
        await onConflict();
      } else {
        setEditorError(caught);
      }
      return;
    }
    setConflict(undefined);
    setEditing(null);
    await afterChange(respondentCode);
  }

  async function confirmRemoval(person: NoveltyPerson) {
    const respondentCode = person.respondentCode ?? '';
    setRemoveError(undefined);
    try {
      await remove.mutateAsync(person);
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409) {
        await onConflict();
      } else {
        setRemoving(null);
        setRemoveError(caught);
        setFocusTarget(respondentCode);
      }
      return;
    }
    setConflict(undefined);
    setRemoving(null);
    await afterChange(respondentCode);
  }

  function noveltyLabel(person: NoveltyPerson): string {
    if (!hasNoveltyAssigned(person)) {
      return '';
    }
    const name = catalog.data?.find((entry) => entry.code === person.noveltyCode)?.name;
    return name ?? `Código ${person.noveltyCode}`;
  }

  const entityList = entities.data ?? [];
  const peopleList = people.data ?? [];
  const editingCode = editing?.respondentCode ?? '';

  return (
    <section aria-labelledby="titulo-novedades">
      <h1 id="titulo-novedades" className="text-2xl font-semibold">
        Gestión de novedades
      </h1>
      <p className="mt-2 text-ink-muted">
        Elija el departamento y la entidad para asignar, cambiar o quitar la novedad de sus
        funcionarios.
      </p>

      <ReportFilters department="required" value={filters} onChange={onFiltersChange} />

      {department !== undefined && (
        <div className="mt-4 grid max-w-xl gap-1">
          <label htmlFor={entityId} className="text-sm font-medium">
            Entidad
          </label>
          <select
            id={entityId}
            className={SELECT_CLASSES}
            value={entity ?? ''}
            disabled={entities.isPending}
            onChange={(event) => {
              setEntity(event.target.value === '' ? undefined : Number(event.target.value));
              setConflict(undefined);
            }}
          >
            <option value="">
              {entities.isPending ? 'Cargando entidades…' : 'Seleccione una entidad'}
            </option>
            {entityList.map((option) => (
              <option key={option.code} value={option.code}>
                {`${option.name} (${formatCount(option.withNovelty ?? 0)} con novedad de ${formatCount(option.sampleSize ?? 0)})`}
              </option>
            ))}
          </select>
        </div>
      )}
      {entities.isError && (
        <ErrorState
          message={errorMessage(entities.error)}
          onRetry={() => void entities.refetch()}
        />
      )}
      {catalog.isError && (
        <ErrorState message={errorMessage(catalog.error)} onRetry={() => void catalog.refetch()} />
      )}

      {conflict && (
        <ConflictNotice
          message={conflict}
          onReload={() => void refreshLists().then(() => setConflict(undefined))}
          focusOnShow
        />
      )}
      {removeError !== undefined && <ErrorState message={errorMessage(removeError)} />}

      <div aria-live="polite">
        {people.isPending && entity !== undefined && (
          <p className="mt-4 text-ink-muted">Cargando funcionarios…</p>
        )}
      </div>
      {people.isError && (
        <ErrorState message={errorMessage(people.error)} onRetry={() => void people.refetch()} />
      )}
      {entity !== undefined &&
        people.data &&
        (peopleList.length === 0 ? (
          <p className="mt-4 text-ink-muted">Esta entidad no tiene funcionarios en la muestra.</p>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full min-w-[40rem] border-collapse text-sm">
              <caption className="sr-only">Funcionarios de la entidad y su novedad</caption>
              <thead className="bg-primary/10">
                <tr>
                  {['Funcionario', 'Estado', 'Novedad', 'Acciones'].map((column) => (
                    <th key={column} scope="col" className="px-3 py-2 text-left font-semibold">
                      {column}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {peopleList.map((person) => {
                  const code = person.respondentCode ?? '';
                  const assigned = hasNoveltyAssigned(person);
                  return (
                    <tr
                      key={code}
                      ref={(element) => {
                        if (element) {
                          rows.current.set(code, element);
                        } else {
                          rows.current.delete(code);
                        }
                      }}
                      tabIndex={-1}
                      className="border-t border-ink-muted/20 focus-visible:outline-2 focus-visible:outline-primary"
                    >
                      <th scope="row" className="px-3 py-2 text-left font-normal">
                        {person.name}
                      </th>
                      <td className="px-3 py-2">
                        {person.status ? STATUS_LABELS[person.status] : ''}
                      </td>
                      <td className="px-3 py-2">
                        {noveltyLabel(person)}
                        {person.otherCause ? `: ${person.otherCause}` : ''}
                      </td>
                      <td className="px-3 py-2">
                        <div className="flex flex-wrap gap-2">
                          <Button
                            variant="secondary"
                            aria-label={`${assigned ? 'Cambiar' : 'Asignar'} novedad de ${person.name}`}
                            onClick={() => {
                              setEditorError(undefined);
                              setEditing(person);
                            }}
                          >
                            {assigned ? 'Cambiar' : 'Asignar'}
                          </Button>
                          {assigned && (
                            <Button
                              variant="secondary"
                              aria-label={`Quitar novedad de ${person.name}`}
                              onClick={() => {
                                setRemoveError(undefined);
                                setRemoving(person);
                              }}
                            >
                              Quitar
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ))}

      {editing && (
        <NoveltyEditor
          key={editingCode}
          open
          personName={editing.name ?? ''}
          hasNovelty={hasNoveltyAssigned(editing)}
          catalog={catalog.data ?? []}
          initialCode={editing.noveltyCode}
          initialOtherCause={editing.otherCause}
          busy={assign.isPending}
          serverError={editorError}
          onSave={(choice) => void save(editing, choice)}
          onCancel={() => {
            setEditing(null);
            setFocusTarget(editingCode);
          }}
        />
      )}
      <ConfirmDialog
        open={removing !== null}
        title="Quitar novedad"
        confirmLabel="Quitar"
        cancelLabel="Cancelar"
        busy={remove.isPending}
        onConfirm={() => removing && void confirmRemoval(removing)}
        onCancel={() => {
          const code = removing?.respondentCode;
          setRemoving(null);
          if (code) {
            setFocusTarget(code);
          }
        }}
      >
        ¿Quitar la novedad de {removing?.name}? El funcionario volverá a estar sin novedad.
      </ConfirmDialog>
    </section>
  );
}
