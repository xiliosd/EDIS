import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useBlocker, useNavigate, useParams } from 'react-router';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorState } from '../../components/ErrorState';
import { LinkButton } from '../../components/LinkButton';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { ApiError } from '../../services/api-error';
import { messageFor } from './answer-error-messages';
import { ErrorSummary } from './ErrorSummary';
import { clearDraft, saveDraft, takeDraft } from './page-drafts';
import { QuestionField, type PageValues } from './questions/QuestionField';
import { evaluate, type RuleSet } from './rules/rule-evaluator';
import { SavedPageSummary } from './SavedPageSummary';
import {
  SessionErrorAlert,
  sessionErrorStatus,
  type SessionErrorStatus,
} from './SessionErrorAlert';
import {
  fetchPage,
  fetchRules,
  pageQueryKey,
  PROGRESS_QUERY_KEY,
  RULES_QUERY_KEY,
  savePageAnswers,
  type PageDetail,
  type QuestionnaireEntry,
} from './survey-api';
import { isLeaveConfirmed, useUnsavedChanges } from './use-unsaved-changes';

function isActiveItem(entry: QuestionnaireEntry): boolean {
  return entry.kind === 'ITEM' && entry.answer?.columnStatus === 'ACTIVE';
}

function itemLabel(entry: QuestionnaireEntry): string {
  return `${entry.label ?? ''} ${entry.text ?? ''}`.trim();
}

export function SurveyPageView() {
  const { pageCode = '' } = useParams();
  const page = useQuery({ queryKey: pageQueryKey(pageCode), queryFn: () => fetchPage(pageCode) });
  const rules = useQuery({ queryKey: RULES_QUERY_KEY, queryFn: fetchRules, staleTime: Infinity });
  const title = page.data ? `${page.data.chapterCode}. ${page.data.chapterTitle}` : 'Encuesta';
  useDocumentTitle(title);
  // El 409 recarga la página y desmonta el formulario; el aviso vive aquí para no perderse.
  const [conflict, setConflict] = useState<{ pageCode: string; message: string } | null>(null);
  // La vista sigue montada al cambiar de página: el aviso se descarta para que no reaparezca
  // si el encuestado vuelve a la página que lo produjo.
  if (conflict && conflict.pageCode !== pageCode) {
    setConflict(null);
  }

  if (page.isPending || rules.isPending) {
    return <p aria-busy="true">Cargando la página…</p>;
  }
  if (page.isError || rules.isError) {
    return (
      <ErrorState
        message="No fue posible cargar la página."
        onRetry={() => {
          void page.refetch();
          void rules.refetch();
        }}
      />
    );
  }
  return (
    <section aria-labelledby="titulo-pagina">
      <p className="text-sm text-ink-muted">{page.data.module}</p>
      <h1 id="titulo-pagina" className="text-2xl font-semibold">
        {title}
      </h1>
      {conflict && (
        <p
          role="alert"
          className="mt-6 rounded-xl border border-danger/40 bg-surface p-4 text-danger"
        >
          {conflict.message}
        </p>
      )}
      {page.data.state === 'LOCKED' && (
        <div className="mt-6">
          <p>Complete primero las páginas anteriores.</p>
          <LinkButton variant="secondary" className="mt-4" to="/encuesta">
            Volver al inicio
          </LinkButton>
        </div>
      )}
      {page.data.state === 'SAVED' && (
        <>
          <SavedPageSummary page={page.data} />
          <LinkButton variant="secondary" className="mt-6" to="/encuesta">
            Volver al inicio
          </LinkButton>
        </>
      )}
      {page.data.state === 'AVAILABLE' && (
        <PageForm
          key={pageCode}
          page={page.data}
          rules={rules.data}
          onConflict={(message) => setConflict({ pageCode, message })}
        />
      )}
    </section>
  );
}

interface PageFormProps {
  page: PageDetail;
  rules: RuleSet;
  onConflict: (message: string) => void;
}

function PageForm({ page, rules, onConflict }: PageFormProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const entries = useMemo(() => page.entries ?? [], [page.entries]);
  const items = useMemo(() => entries.filter(isActiveItem), [entries]);
  const columns = useMemo(() => items.map((entry) => entry.answer?.columnCode ?? ''), [items]);
  const labels = useMemo(
    () =>
      Object.fromEntries(items.map((entry) => [entry.answer?.columnCode ?? '', itemLabel(entry)])),
    [items],
  );
  const form = useForm<PageValues>({
    defaultValues: Object.fromEntries(
      items.map((entry) => [
        entry.answer?.columnCode ?? '',
        entry.answer?.inputType === 'CHECKBOX' ? '0' : '',
      ]),
    ),
  });
  const values = form.watch();
  const evaluation = evaluate(rules, values, columns);
  // El archivo de reglas cubre toda la encuesta: solo cuentan las preguntas de esta página.
  const pageDisabled = evaluation.disabled.filter((column) => columns.includes(column));
  const checkboxColumns = useMemo(
    () =>
      new Set(
        items
          .filter((entry) => entry.answer?.inputType === 'CHECKBOX')
          .map((entry) => entry.answer?.columnCode ?? ''),
      ),
    [items],
  );
  const disabled = new Set(pageDisabled);
  const [summary, setSummary] = useState<Record<string, string>>({});
  const [pending, setPending] = useState<PageValues | null>(null);
  const [alertMessage, setAlertMessage] = useState('');
  const [sessionError, setSessionError] = useState<SessionErrorStatus | null>(null);
  const summaryRef = useRef<HTMLDivElement>(null);
  const { isDirty } = form.formState;
  useUnsavedChanges(isDirty);
  // Desde que se confirma el guardado no se bloquea: el paso a la página siguiente ocurre antes de
  // que el formulario limpio vuelva a pintarse. Se reactiva si el guardado falla.
  const savingRef = useRef(false);
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      isDirty &&
      !savingRef.current &&
      !isLeaveConfirmed(nextLocation.state) &&
      // Los enlaces del resumen de errores (#campo-…) no salen de la página.
      currentLocation.pathname !== nextLocation.pathname,
  );

  // Una pregunta que deja de aplicar pierde su valor, como en el formulario heredado. La casilla
  // vuelve a "0" (no marcada) para que, si vuelve a aplicar, no quede vacía y sin responder.
  const disabledKey = pageDisabled.join(',');
  useEffect(() => {
    for (const column of disabledKey ? disabledKey.split(',') : []) {
      const clearedValue = checkboxColumns.has(column) ? '0' : '';
      if (form.getValues(column) !== clearedValue) {
        form.setValue(column, clearedValue);
      }
    }
  }, [disabledKey, checkboxColumns, form]);

  // Si la sesión venció al guardar, las respuestas quedaron en un borrador local: se recuperan
  // una sola vez y quedan como cambios sin guardar (el aviso de salida sigue activo).
  useEffect(() => {
    const draft = takeDraft(page.pageCode ?? '');
    if (!draft) {
      return;
    }
    for (const column of columns) {
      if (column in draft) {
        form.setValue(column, draft[column], { shouldDirty: true });
      }
    }
  }, [page.pageCode, columns, form]);

  useEffect(() => {
    if (Object.keys(summary).length > 0) {
      summaryRef.current?.focus();
    }
  }, [summary]);

  function showErrors(messages: Record<string, string>) {
    form.clearErrors();
    Object.entries(messages).forEach(([column, message]) =>
      form.setError(column, { type: 'server', message }),
    );
    setSummary(messages);
  }

  const save = useMutation({
    mutationFn: (answers: PageValues) => savePageAnswers(page.pageCode ?? '', answers),
    onMutate: () => {
      savingRef.current = true;
    },
    onSuccess: async (result) => {
      clearDraft(page.pageCode ?? '');
      form.reset(form.getValues());
      setPending(null);
      await queryClient.invalidateQueries({ queryKey: PROGRESS_QUERY_KEY });
      await queryClient.invalidateQueries({ queryKey: pageQueryKey(page.pageCode ?? '') });
      void navigate(
        result.nextPageCode ? `/encuesta/pagina/${result.nextPageCode}` : '/encuesta/terminar',
      );
    },
    onError: (error) => {
      savingRef.current = false;
      setPending(null);
      const sessionStatus = sessionErrorStatus(error);
      if (sessionStatus === 401) {
        saveDraft(page.pageCode ?? '', form.getValues());
      }
      if (sessionStatus) {
        setSessionError(sessionStatus);
      } else if (error instanceof ApiError && error.status === 422) {
        if (Object.keys(error.fieldErrors).length > 0) {
          showErrors(error.fieldErrors);
        } else {
          setAlertMessage(error.detail);
        }
      } else if (error instanceof ApiError && error.status === 409) {
        onConflict(error.detail);
        void queryClient.invalidateQueries({ queryKey: pageQueryKey(page.pageCode ?? '') });
      } else {
        setAlertMessage('No fue posible guardar. Intente de nuevo.');
      }
    },
  });

  function onSubmit(data: PageValues) {
    setAlertMessage('');
    setSessionError(null);
    const result = evaluate(rules, data, columns);
    const messages = Object.fromEntries(
      Object.entries(result.errors).map(([column, code]) => [column, messageFor(code)]),
    );
    if (Object.keys(messages).length > 0) {
      showErrors(messages);
      return;
    }
    setSummary({});
    const excluded = new Set(result.disabled);
    setPending(
      Object.fromEntries(
        columns.map((column) => [column, excluded.has(column) ? '' : (data[column] ?? '').trim()]),
      ),
    );
  }

  return (
    <form noValidate onSubmit={form.handleSubmit(onSubmit)} className="mt-6">
      {Object.keys(summary).length > 0 && (
        <ErrorSummary ref={summaryRef} errors={summary} labels={labels} />
      )}
      {sessionError && <SessionErrorAlert status={sessionError} />}
      {alertMessage && (
        <p
          role="alert"
          className="mt-6 rounded-xl border border-danger/40 bg-surface p-4 text-danger"
        >
          {alertMessage}
        </p>
      )}
      <p aria-live="polite" className="sr-only">
        {disabled.size > 0 &&
          (disabled.size === 1
            ? '1 pregunta no aplica según sus respuestas.'
            : `${disabled.size} preguntas no aplican según sus respuestas.`)}
      </p>
      <div className="mt-6 grid gap-6">
        {entries.map((entry) => (
          <div key={entry.id}>
            {(entry.headers ?? []).map((header, index) =>
              header.kind === 'HEADING' ? (
                <h2 key={index} className="mb-2 text-xl font-semibold">
                  {header.text}
                </h2>
              ) : (
                <h3 key={index} className="mb-2 text-lg font-medium">
                  {header.text}
                </h3>
              ),
            )}
            {entry.kind === 'STATEMENT' && <p className="font-semibold">{itemLabel(entry)}</p>}
            {isActiveItem(entry) &&
              (disabled.has(entry.answer?.columnCode ?? '') ? (
                <div className="rounded-lg border border-dashed border-ink-muted/40 p-3 text-ink-muted">
                  <p>{itemLabel(entry)}</p>
                  <p className="text-sm">No aplica según sus respuestas anteriores.</p>
                </div>
              ) : (
                <QuestionField
                  entry={entry}
                  column={entry.answer?.columnCode ?? ''}
                  label={itemLabel(entry)}
                  error={form.formState.errors[entry.answer?.columnCode ?? '']?.message}
                  register={form.register}
                  control={form.control}
                  rules={rules}
                />
              ))}
          </div>
        ))}
      </div>
      <div className="mt-8 flex flex-wrap gap-3">
        <Button type="submit">Guardar página</Button>
        <Button
          variant="secondary"
          onClick={() => {
            void navigate('/encuesta');
          }}
        >
          Volver al inicio
        </Button>
      </div>
      <ConfirmDialog
        open={pending !== null}
        title="¿Guardar esta página?"
        confirmLabel="Guardar y continuar"
        cancelLabel="Revisar de nuevo"
        busy={save.isPending}
        onConfirm={() => {
          if (pending && !save.isPending) {
            save.mutate(pending);
          }
        }}
        onCancel={() => setPending(null)}
      >
        Después de guardar no podrá cambiar las respuestas de esta página.
      </ConfirmDialog>
      <ConfirmDialog
        open={blocker.state === 'blocked'}
        title="¿Salir sin guardar?"
        confirmLabel="Salir sin guardar"
        cancelLabel="Seguir en la página"
        onConfirm={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      >
        Tiene respuestas sin guardar en esta página. Si sale, se perderán.
      </ConfirmDialog>
    </form>
  );
}
