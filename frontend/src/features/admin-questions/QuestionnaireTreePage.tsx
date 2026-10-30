import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ErrorState } from '../../components/ErrorState';
import { useDocumentTitle } from '../../hooks/use-document-title';
import {
  fetchQuestionnaire,
  questionnaireQueryKey,
  reorder,
  type AdminEditResult,
  type AdminPage,
  type AdminQuestionnaireView,
  type TablesResult,
} from './admin-api';
import { AddQuestionDialog, type OptionGroupChoice } from './AddQuestionDialog';
import { QuestionEditor } from './QuestionEditor';
import { ReorderList } from './ReorderList';
import { TablesMappingPanel } from './TablesMappingPanel';
import { entryLabel, errorMessage, handleEditError, type TreeEditing } from './tree-editing';

const STATE_LABELS: Record<string, string> = { borrador: 'Borrador', publicado: 'Publicado' };

/** Grupos de opciones que ya usa el árbol; son los únicos que se ofrecen al agregar. */
function optionGroupsOf(view: AdminQuestionnaireView): OptionGroupChoice[] {
  const groups = new Map<number, string>();
  for (const page of view.pages ?? []) {
    for (const item of page.entries ?? []) {
      if (item.optionGroup != null && !groups.has(item.optionGroup)) {
        groups.set(item.optionGroup, entryLabel(item));
      }
    }
  }
  return [...groups.entries()]
    .sort(([first], [second]) => first - second)
    .map(([group, example]) => ({ group, example }));
}

function missingChaptersOf(view: AdminQuestionnaireView): Set<string> {
  return new Set(
    (view.pages ?? []).filter((page) => page.tableMissing).map((page) => page.chapterCode ?? ''),
  );
}

/** Árbol del cuestionario de un año: páginas → preguntas, con edición si el año es borrador. */
export function QuestionnaireTreePage() {
  const { encuesta = '', anio = '' } = useParams();
  const year = Number(anio);
  const title = `Cuestionario ${encuesta} ${anio}`;
  useDocumentTitle(title);
  const queryClient = useQueryClient();
  const queryKey = questionnaireQueryKey(encuesta, year);
  const treeQuery = useQuery({
    queryKey,
    queryFn: () => fetchQuestionnaire(encuesta, year),
  });
  const [conflict, setConflict] = useState<string | null>(null);
  const [skipWarnings, setSkipWarnings] = useState<Record<string, string[]>>({});
  const [reorderErrors, setReorderErrors] = useState<Record<string, string>>({});
  // Al recargar tras un conflicto se reinician los editores abiertos con texto viejo.
  const [reloads, setReloads] = useState(0);
  const [statusMessage, setStatusMessage] = useState('');
  // Id del elemento que recibe el foco cuando la pregunta enfocada sale de la lista.
  const [focusTargetId, setFocusTargetId] = useState<string | null>(null);

  const view = treeQuery.data;

  /** La respuesta trae la página releída y la nueva versión: reemplaza la de la caché. */
  function applyEdit(result: AdminEditResult) {
    queryClient.setQueryData<AdminQuestionnaireView>(queryKey, (current) =>
      current
        ? {
            ...current,
            version: result.version ?? current.version,
            pages: (current.pages ?? []).map((page) =>
              result.page && page.pageCode === result.page.pageCode ? result.page : page,
            ),
          }
        : current,
    );
  }

  useEffect(() => {
    if (focusTargetId) {
      document.getElementById(focusTargetId)?.focus();
      setFocusTargetId(null);
    }
  }, [focusTargetId, treeQuery.data]);

  /**
   * La pregunta desactivada desaparece con el botón enfocado (WCAG 2.4.3): el foco pasa al
   * encabezado de la siguiente, o de la anterior, o al título de la página si era la única.
   */
  function applyDeactivation(result: AdminEditResult, capProyId: number, columnLabel: string) {
    const pageCode = result.page?.pageCode ?? '';
    const before = view?.pages?.find((page) => page.pageCode === pageCode)?.entries ?? [];
    const index = before.findIndex((item) => item.capProyId === capProyId);
    const neighbour = before[index + 1] ?? before[index - 1];
    applyEdit(result);
    setStatusMessage(`Se desactivó ${columnLabel}.`);
    setFocusTargetId(
      neighbour ? `pregunta-${pageCode}-${neighbour.capProyId}-titulo` : `pagina-${pageCode}`,
    );
  }

  function applyTables(result: TablesResult) {
    queryClient.setQueryData<AdminQuestionnaireView>(queryKey, (current) =>
      current
        ? {
            ...current,
            version: result.version ?? current.version,
            tables: result.tables ?? current.tables,
          }
        : current,
    );
    // Cambiar una tabla cambia el estado de columna de sus preguntas: se relee el árbol.
    void queryClient.invalidateQueries({ queryKey });
  }

  function reload() {
    setConflict(null);
    setSkipWarnings({});
    setReorderErrors({});
    setReloads((count) => count + 1);
    void treeQuery.refetch();
  }

  const editing: TreeEditing | null =
    view && view.state === 'borrador'
      ? {
          survey: encuesta,
          year,
          version: view.version ?? 0,
          onEdited: applyEdit,
          onDeactivated: applyDeactivation,
          onConflict: setConflict,
        }
      : null;

  async function reorderPage(page: AdminPage, ids: number[]): Promise<boolean> {
    if (!editing) {
      return false;
    }
    const pageCode = page.pageCode ?? '';
    setReorderErrors((current) => ({ ...current, [pageCode]: '' }));
    try {
      const result = await reorder(encuesta, year, pageCode, ids, editing.version);
      applyEdit(result);
      setSkipWarnings((current) => ({
        ...current,
        [pageCode]: result.skipsTargetingEarlierQuestions ?? [],
      }));
      return true;
    } catch (caught) {
      const message = handleEditError(caught, editing);
      if (message !== null) {
        setReorderErrors((current) => ({ ...current, [pageCode]: message }));
      }
      return false;
    }
  }

  return (
    <section aria-labelledby="titulo-arbol">
      <Link
        to="/admin/cuestionario"
        className="inline-flex min-h-11 items-center text-primary underline"
      >
        Volver a los años
      </Link>
      <h1 id="titulo-arbol" className="text-2xl font-semibold">
        {title}
      </h1>

      {treeQuery.isPending && (
        <p role="status" aria-live="polite" className="mt-6 text-ink-muted">
          Cargando cuestionario…
        </p>
      )}
      {treeQuery.isError && (
        <ErrorState
          message={errorMessage(treeQuery.error)}
          onRetry={() => void treeQuery.refetch()}
        />
      )}

      <p role="status" className="sr-only">
        {statusMessage}
      </p>

      {conflict && (
        <div role="alert" className="mt-6 rounded-xl border border-danger/40 bg-surface p-4">
          <p className="text-danger">{conflict}</p>
          <Button variant="secondary" className="mt-3" onClick={reload}>
            Recargar
          </Button>
        </div>
      )}

      {view && (
        <div key={reloads}>
          <p className="mt-2 text-ink-muted">
            Estado: {STATE_LABELS[view.state ?? ''] ?? view.state} ·{' '}
            <span>Versión {view.version}</span>
          </p>
          {!editing && (
            <p className="mt-4 rounded-xl border border-ink-muted/30 bg-surface p-4">
              El cuestionario de {view.year} está publicado y es de solo lectura.
            </p>
          )}

          <TablesMappingPanel
            tables={view.tables ?? {}}
            missingChapters={missingChaptersOf(view)}
            editing={editing}
            onSaved={applyTables}
          />

          {(view.pages ?? []).map((page) => {
            const pageCode = page.pageCode ?? '';
            const entries = page.entries ?? [];
            const warnings = skipWarnings[pageCode] ?? [];
            return (
              <section key={pageCode} aria-labelledby={`pagina-${pageCode}`} className="mt-8">
                <h2 id={`pagina-${pageCode}`} tabIndex={-1} className="text-xl font-semibold">
                  Página {pageCode}
                </h2>
                <p className="text-ink-muted">
                  {`Capítulo ${page.chapterCode} · Tabla ${page.tableName}`}
                </p>
                {entries.length === 0 ? (
                  <p className="mt-4">Esta página no tiene preguntas en {view.year}.</p>
                ) : (
                  <ReorderList
                    label={`Preguntas de la página ${pageCode}`}
                    items={entries}
                    getId={(item) => item.capProyId ?? 0}
                    getLabel={entryLabel}
                    editable={editing !== null}
                    onReorder={(ids) => reorderPage(page, ids)}
                    renderItem={(item) => (
                      <QuestionEditor entry={item} pageCode={pageCode} editing={editing} />
                    )}
                  />
                )}
                {reorderErrors[pageCode] && (
                  <p
                    role="alert"
                    className="mt-3 rounded-xl border border-danger/40 bg-surface p-3 text-danger"
                  >
                    {reorderErrors[pageCode]}
                  </p>
                )}
                {warnings.length > 0 && (
                  <div className="mt-3 rounded-xl border border-ink-muted/30 bg-surface p-3">
                    <p className="font-medium">Revise estos saltos tras el nuevo orden:</p>
                    <ul className="mt-2 list-disc pl-6">
                      {warnings.map((warning) => (
                        <li key={warning}>{warning}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {editing && (
                  <AddQuestionDialog
                    page={page}
                    optionGroups={optionGroupsOf(view)}
                    editing={editing}
                  />
                )}
              </section>
            );
          })}
        </div>
      )}
    </section>
  );
}
