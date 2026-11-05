import { useId, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ConflictNotice } from '../../components/ConflictNotice';
import { ErrorState } from '../../components/ErrorState';
import { QuestionnaireTabs } from '../../components/QuestionnaireTabs';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { ApiError } from '../../services/api-error';
import { questionnaireQueryKey } from '../admin-questions/admin-api';
import { errorMessage } from '../admin-questions/tree-editing';
import { AnalysisSummary } from '../admin-rules/AnalysisSummary';
import { rulesExportQueryKey, rulesQueryKey, type AnalysisReport } from '../admin-rules/rules-api';
import { ruleSetQueryKey } from '../admin-publish/publish-api';
import {
  fetchHistory,
  fetchHistoryVersion,
  historyQueryKey,
  historyVersionQueryKey,
  restoreVersion,
} from './history-api';
import { versionDiff, type Change } from './version-diff';

function formatDate(iso: string | undefined): string {
  return iso ? new Date(iso).toLocaleString('es-CO') : '';
}

const DIFF_SECTIONS = [
  { key: 'skips', title: 'Saltos' },
  { key: 'validations', title: 'Validaciones' },
  { key: 'cases', title: 'Casos de prueba' },
  { key: 'tables', title: 'Tablas de respuesta' },
] as const;

function ChangeList({ title, changes }: { title: string; changes: Change[] }) {
  return (
    <section className="mt-4">
      <h3 className="font-semibold">{title}</h3>
      {changes.length === 0 ? (
        <p className="text-ink-muted">Sin cambios.</p>
      ) : (
        <ul className="mt-2 grid gap-2">
          {changes.map((change) => (
            <li key={change.id} className="rounded-lg border border-ink-muted/30 p-3">
              <p>
                <strong>{change.id}</strong> — <span>{change.kind}</span>
              </p>
              {change.before !== undefined && (
                <p className="break-words">
                  <span className="font-medium">Antes: </span>
                  <code>{change.before}</code>
                </p>
              )}
              {change.after !== undefined && (
                <p className="break-words">
                  <span className="font-medium">Después: </span>
                  <code>{change.after}</code>
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

interface DiffPanelProps {
  survey: string;
  year: number;
  versions: number[];
}

/** Compara dos versiones elegidas; siempre se muestra de la más antigua a la más nueva. */
function DiffPanel({ survey, year, versions }: DiffPanelProps) {
  const firstId = useId();
  const secondId = useId();
  // Las versiones llegan de la más reciente a la más antigua: por defecto, anterior y actual.
  const [first, setFirst] = useState(versions[1] ?? versions[0]);
  const [second, setSecond] = useState(versions[0]);
  const older = Math.min(first, second);
  const newer = Math.max(first, second);
  const olderQuery = useQuery({
    queryKey: historyVersionQueryKey(survey, year, older),
    queryFn: () => fetchHistoryVersion(survey, year, older),
  });
  const newerQuery = useQuery({
    queryKey: historyVersionQueryKey(survey, year, newer),
    queryFn: () => fetchHistoryVersion(survey, year, newer),
  });
  const diff = useMemo(
    () =>
      olderQuery.data && newerQuery.data ? versionDiff(olderQuery.data, newerQuery.data) : null,
    [olderQuery.data, newerQuery.data],
  );
  const selectClass = 'min-h-11 rounded-lg border border-ink-muted bg-surface px-3';

  return (
    <section aria-labelledby="titulo-diferencias" className="mt-8">
      <h2 id="titulo-diferencias" className="text-xl font-semibold">
        Comparar versiones
      </h2>
      <div className="mt-3 flex flex-wrap gap-4">
        <div className="grid gap-1">
          <label htmlFor={firstId}>Primera versión a comparar</label>
          <select
            id={firstId}
            className={selectClass}
            value={first}
            onChange={(event) => setFirst(Number(event.target.value))}
          >
            {versions.map((version) => (
              <option key={version} value={version}>
                {version}
              </option>
            ))}
          </select>
        </div>
        <div className="grid gap-1">
          <label htmlFor={secondId}>Segunda versión a comparar</label>
          <select
            id={secondId}
            className={selectClass}
            value={second}
            onChange={(event) => setSecond(Number(event.target.value))}
          >
            {versions.map((version) => (
              <option key={version} value={version}>
                {version}
              </option>
            ))}
          </select>
        </div>
      </div>

      <section aria-label="Diferencias entre versiones" aria-live="polite" className="mt-4">
        <p className="font-medium">
          Versión {older} → versión {newer}
        </p>
        {(olderQuery.isError || newerQuery.isError) && (
          <ErrorState
            message={errorMessage(olderQuery.error ?? newerQuery.error)}
            onRetry={() => {
              void olderQuery.refetch();
              void newerQuery.refetch();
            }}
          />
        )}
        {!diff && !olderQuery.isError && !newerQuery.isError && (
          <p role="status" className="text-ink-muted">
            Cargando las versiones…
          </p>
        )}
        {diff &&
          (older === newer ? (
            <p>Elija dos versiones distintas.</p>
          ) : (
            DIFF_SECTIONS.map(({ key, title }) => (
              <ChangeList key={key} title={title} changes={diff[key]} />
            ))
          ))}
      </section>
    </section>
  );
}

/** Historial de versiones del año: comparar dos versiones y restaurar una en un borrador. */
export function HistoryPage() {
  const { encuesta = '', anio = '' } = useParams();
  const year = Number(anio);
  const title = `Historial ${encuesta} ${anio}`;
  useDocumentTitle(title);
  const queryClient = useQueryClient();
  const historyQuery = useQuery({
    queryKey: historyQueryKey(encuesta, year),
    queryFn: () => fetchHistory(encuesta, year),
  });
  const [restoring, setRestoring] = useState<number | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [rejection, setRejection] = useState<AnalysisReport | null>(null);
  const [statusMessage, setStatusMessage] = useState('');

  const history = historyQuery.data;
  const entries = history?.entries ?? [];
  const isDraft = history?.state === 'borrador';

  const restore = useMutation({
    mutationFn: (target: number) => restoreVersion(encuesta, year, target, history?.version ?? 0),
    onSuccess: (_result, target) => {
      // Restaurar crea una versión nueva: todo lo que depende del archivo de reglas se relee.
      void queryClient.invalidateQueries({ queryKey: historyQueryKey(encuesta, year) });
      void queryClient.invalidateQueries({ queryKey: rulesQueryKey(encuesta, year) });
      void queryClient.invalidateQueries({ queryKey: rulesExportQueryKey(encuesta, year) });
      void queryClient.invalidateQueries({ queryKey: ruleSetQueryKey(encuesta, year) });
      void queryClient.invalidateQueries({ queryKey: questionnaireQueryKey(encuesta, year) });
      setStatusMessage(`Se restauró la versión ${target} como versión nueva.`);
    },
    onError: (caught) => {
      if (caught instanceof ApiError && caught.status === 422 && caught.report) {
        setRejection(caught.report);
      } else if (caught instanceof ApiError && caught.status === 409) {
        setConflict(caught.detail);
      } else {
        setGeneralError(errorMessage(caught));
      }
    },
  });

  function reload() {
    setConflict(null);
    void historyQuery.refetch();
  }

  function confirmRestore() {
    const target = restoring;
    setRestoring(null);
    setConflict(null);
    setGeneralError(null);
    setRejection(null);
    setStatusMessage('');
    if (target !== null) {
      restore.mutate(target);
    }
  }

  return (
    <section aria-labelledby="titulo-historial">
      <Link
        to="/admin/cuestionario"
        className="inline-flex min-h-11 items-center text-primary underline"
      >
        Volver a los años
      </Link>
      <h1 id="titulo-historial" className="text-2xl font-semibold">
        {title}
      </h1>
      <QuestionnaireTabs survey={encuesta} year={year} />

      {historyQuery.isPending && (
        <p role="status" aria-live="polite" className="mt-6 text-ink-muted">
          Cargando el historial…
        </p>
      )}
      {historyQuery.isError && (
        <ErrorState message={errorMessage(historyQuery.error)} onRetry={reload} />
      )}

      <p role="status" aria-live="polite" className="mt-4">
        {statusMessage}
      </p>
      {conflict && <ConflictNotice message={conflict} onReload={reload} focusOnShow />}
      {generalError && (
        <p
          role="alert"
          className="mt-6 rounded-xl border border-danger/40 bg-surface p-4 text-danger"
        >
          {generalError}
        </p>
      )}
      {rejection && <AnalysisSummary report={rejection} survey={encuesta} year={year} rejected />}

      {history && (
        <>
          {!isDraft && (
            <p className="mt-4 text-ink-muted">
              El año está publicado: solo se puede restaurar en un borrador.
            </p>
          )}
          <div className="mt-4 overflow-x-auto">
            <table className="w-full border-collapse text-left">
              <caption className="mb-2 text-left font-semibold">Versiones guardadas</caption>
              <thead>
                <tr className="border-b border-ink-muted/40">
                  <th scope="col" className="p-2">
                    Versión
                  </th>
                  <th scope="col" className="p-2">
                    Fecha
                  </th>
                  <th scope="col" className="p-2">
                    Usuario
                  </th>
                  <th scope="col" className="p-2">
                    Operación
                  </th>
                  <th scope="col" className="p-2">
                    Motivo
                  </th>
                  <th scope="col" className="p-2">
                    Actual
                  </th>
                  <th scope="col" className="p-2">
                    <span className="sr-only">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <tr key={entry.version} className="border-b border-ink-muted/20">
                    <th scope="row" className="p-2 font-medium">
                      {entry.version}
                    </th>
                    <td className="p-2">{formatDate(entry.savedAt)}</td>
                    <td className="p-2">{entry.user}</td>
                    <td className="p-2">{entry.operation}</td>
                    <td className="p-2">{entry.reason}</td>
                    <td className="p-2">{entry.current ? 'Sí' : 'No'}</td>
                    <td className="p-2">
                      {!entry.current && (
                        <Button
                          variant="secondary"
                          disabled={!isDraft || restore.isPending}
                          aria-label={`Restaurar esta versión ${entry.version}`}
                          onClick={() => setRestoring(entry.version ?? 0)}
                        >
                          Restaurar esta versión
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {entries.length >= 2 && (
            <DiffPanel
              key={entries[0]?.version}
              survey={encuesta}
              year={year}
              versions={entries.map((entry) => entry.version ?? 0)}
            />
          )}
        </>
      )}

      {restoring !== null && (
        <ConfirmDialog
          open
          title="Restaurar una versión"
          confirmLabel="Restaurar"
          cancelLabel="Cancelar"
          onCancel={() => setRestoring(null)}
          onConfirm={confirmRestore}
        >
          <p>
            Se reemplazarán saltos, validaciones, casos y tablas por los de la versión {restoring} y
            se guardará como versión nueva. Los textos y preguntas (tablas FRM_*) no se restauran.
          </p>
        </ConfirmDialog>
      )}
    </section>
  );
}
