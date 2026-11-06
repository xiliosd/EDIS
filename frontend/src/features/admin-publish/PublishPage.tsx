import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ConflictNotice } from '../../components/ConflictNotice';
import { ErrorState } from '../../components/ErrorState';
import { QuestionnaireTabs } from '../../components/QuestionnaireTabs';
import { READ_ONLY_NOTE_ID, ReadOnlyNotice } from '../../components/ReadOnlyNotice';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { ApiError } from '../../services/api-error';
import { useQuestionnaireReadOnly } from '../admin/environment-api';
import { questionnaireQueryKey } from '../admin-questions/admin-api';
import { errorMessage } from '../admin-questions/tree-editing';
import { rulesExportQueryKey, rulesQueryKey } from '../admin-rules/rules-api';
import { ChecklistView } from './ChecklistView';
import {
  checklistQueryKey,
  fetchChecklist,
  fetchRuleSet,
  publishYear,
  ruleSetQueryKey,
  type PublicationChecklist,
} from './publish-api';

function formatDate(iso: string | undefined): string {
  return iso ? new Date(iso).toLocaleString('es-CO') : '';
}

/** Lista de comprobación del año y botón para publicarlo. */
export function PublishPage() {
  const { encuesta = '', anio = '' } = useParams();
  const year = Number(anio);
  const title = `Publicar ${encuesta} ${anio}`;
  useDocumentTitle(title);
  const queryClient = useQueryClient();
  const readOnly = useQuestionnaireReadOnly();
  const checklistKey = checklistQueryKey(encuesta, year);
  const checklistQuery = useQuery({
    queryKey: checklistKey,
    queryFn: () => fetchChecklist(encuesta, year),
  });
  const ruleSetQuery = useQuery({
    queryKey: ruleSetQueryKey(encuesta, year),
    queryFn: () => fetchRuleSet(encuesta, year),
  });
  const [confirming, setConfirming] = useState(false);
  const [conflict, setConflict] = useState<string | null>(null);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState('');
  // Un 422 reemplaza la lista por la que devuelve el servidor y lleva el foco al resumen.
  const [focusSummary, setFocusSummary] = useState(0);
  const summaryRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (focusSummary > 0) {
      summaryRef.current?.focus();
    }
  }, [focusSummary]);

  const publish = useMutation({
    mutationFn: (version: number) => publishYear(encuesta, year, version),
    onSuccess: (checklist) => {
      queryClient.setQueryData(checklistKey, checklist);
      // Publicar cambia el estado de todo lo que el año muestra: se vuelve a leer.
      void queryClient.invalidateQueries({ queryKey: ruleSetQueryKey(encuesta, year) });
      void queryClient.invalidateQueries({ queryKey: questionnaireQueryKey(encuesta, year) });
      void queryClient.invalidateQueries({ queryKey: rulesQueryKey(encuesta, year) });
      void queryClient.invalidateQueries({ queryKey: rulesExportQueryKey(encuesta, year) });
      setStatusMessage(`El año ${year} quedó publicado.`);
    },
    onError: (caught) => {
      if (caught instanceof ApiError && caught.status === 422 && caught.checklist) {
        queryClient.setQueryData<PublicationChecklist>(checklistKey, caught.checklist);
        setFocusSummary((count) => count + 1);
      } else if (caught instanceof ApiError && caught.status === 409) {
        setConflict(caught.detail);
      } else {
        setGeneralError(errorMessage(caught));
      }
    },
  });

  const checklist = checklistQuery.data;
  const ruleSet = ruleSetQuery.data;
  const published = ruleSet?.estado === 'publicado';
  const publication = ruleSet?.publicacion;

  function reload() {
    setConflict(null);
    void checklistQuery.refetch();
    void ruleSetQuery.refetch();
  }

  function confirmPublish() {
    setConfirming(false);
    setConflict(null);
    setGeneralError(null);
    publish.mutate(checklist?.version ?? 0);
  }

  return (
    <section aria-labelledby="titulo-publicar">
      <Link
        to="/admin/cuestionario"
        className="inline-flex min-h-11 items-center text-primary underline"
      >
        Volver a los años
      </Link>
      <h1 id="titulo-publicar" className="text-2xl font-semibold">
        {title}
      </h1>
      <QuestionnaireTabs survey={encuesta} year={year} />
      <ReadOnlyNotice show={readOnly} />

      {(checklistQuery.isPending || ruleSetQuery.isPending) &&
        !checklistQuery.isError &&
        !ruleSetQuery.isError && (
          <p role="status" aria-live="polite" className="mt-6 text-ink-muted">
            Cargando la lista de publicación…
          </p>
        )}
      {(checklistQuery.isError || ruleSetQuery.isError) && (
        <ErrorState
          message={errorMessage(checklistQuery.error ?? ruleSetQuery.error)}
          onRetry={reload}
        />
      )}

      <p role="status" aria-live="polite" className="sr-only">
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

      {checklist && ruleSet && (
        <>
          {published && publication && (
            <p className="mt-4 rounded-xl border border-ink-muted/30 bg-surface p-4">
              Publicado el {formatDate(publication.fecha)} por {publication.usuario}, versión{' '}
              {publication.version}
            </p>
          )}
          <ChecklistView
            ref={summaryRef}
            checklist={checklist}
            survey={encuesta}
            year={year}
            warnings={published ? (publication?.avisos ?? []) : (checklist.warnings ?? [])}
          />
          <Button
            className="mt-6"
            disabled={readOnly || published || !checklist.ready || publish.isPending}
            aria-describedby={readOnly ? READ_ONLY_NOTE_ID : undefined}
            aria-busy={publish.isPending || undefined}
            onClick={() => setConfirming(true)}
          >
            Publicar
          </Button>
          {published && (
            <p className="mt-2 text-ink-muted">
              El año ya está publicado: solo admite correcciones.
            </p>
          )}
        </>
      )}

      {confirming && (
        <ConfirmDialog
          open
          title="Publicar el año"
          confirmLabel="Confirmar publicación"
          cancelLabel="Cancelar"
          onCancel={() => setConfirming(false)}
          onConfirm={confirmPublish}
        >
          <p>Publicar bloquea la edición estructural del año {year}. ¿Continuar?</p>
        </ConfirmDialog>
      )}
    </section>
  );
}
