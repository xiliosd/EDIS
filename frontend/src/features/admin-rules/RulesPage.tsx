import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ConflictNotice } from '../../components/ConflictNotice';
import { ErrorState } from '../../components/ErrorState';
import { QuestionnaireTabs } from '../../components/QuestionnaireTabs';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { ApiError } from '../../services/api-error';
import { errorMessage } from '../admin-questions/tree-editing';
import { AnalysisSummary, FindingNotes, findingsOfElements } from './AnalysisSummary';
import { describeRule } from './rule-description';
import {
  deleteSkip,
  deleteValidation,
  fetchRules,
  importRules,
  rulesQueryKey,
  simulatorQueryKey,
  saveSkip,
  saveValidation,
  type AnalysisReport,
  type Condition,
  type FieldValidation,
  type RuleQuestion,
  type RulesEditorView,
  type RulesEditResult,
  type RuleSet,
  type SkipRule,
} from './rules-api';
import { RulesJsonPanel } from './RulesJsonPanel';
import { SkipRuleBuilder } from './SkipRuleBuilder';
import { ValidationEditor } from './ValidationEditor';

const STATE_LABELS: Record<string, string> = { borrador: 'Borrador', publicado: 'Publicado' };
/** Valor del selector para las reglas que no tienen ninguna columna conocida. */
const NO_PAGE = '__sin-pagina__';

/**
 * Resultado de una edición: `ok` y `handled` (409) cierran el diálogo; `rejected` (422) y `error`
 * lo dejan abierto con el borrador intacto.
 */
type EditOutcome = { status: 'ok' | 'handled' | 'rejected' } | { status: 'error'; message: string };

function columnsOfCondition(condition: Condition | undefined): string[] {
  if (!condition) {
    return [];
  }
  return [
    ...(condition.pregunta ? [condition.pregunta] : []),
    ...(condition.todas ?? []),
    ...(condition.alguna ?? []),
    ...(condition.y ?? []).flatMap(columnsOfCondition),
    ...(condition.o ?? []).flatMap(columnsOfCondition),
  ];
}

function targetsOf(rule: SkipRule): string[] {
  return rule.entonces?.preguntas ?? [];
}

interface FindingKey {
  code?: string;
  element?: string;
  questions?: string[];
}

function sameFinding(first: FindingKey, second: FindingKey): boolean {
  return (
    first.code === second.code &&
    first.element === second.element &&
    JSON.stringify(first.questions ?? []) === JSON.stringify(second.questions ?? [])
  );
}

/** Errores que el rechazo trae y el archivo actual todavía no tenía. */
function newErrorsOf(
  rejected: AnalysisReport,
  current: AnalysisReport | undefined,
): AnalysisReport {
  const known = current?.errors ?? [];
  return {
    errors: (rejected.errors ?? []).filter((item) => !known.some((old) => sameFinding(old, item))),
    warnings: [],
  };
}

/** Reglas y validaciones de un año: constructor de saltos, análisis y vista JSON. */
export function RulesPage() {
  const { encuesta = '', anio = '' } = useParams();
  const year = Number(anio);
  const title = `Reglas ${encuesta} ${anio}`;
  useDocumentTitle(title);
  const queryClient = useQueryClient();
  const queryKey = rulesQueryKey(encuesta, year);
  const rulesQuery = useQuery({ queryKey, queryFn: () => fetchRules(encuesta, year) });

  const [chosenPage, setChosenPage] = useState<string | null>(null);
  const [builder, setBuilder] = useState<{ rule?: SkipRule } | null>(null);
  const [builderError, setBuilderError] = useState<string | null>(null);
  const [toDelete, setToDelete] = useState<SkipRule | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [conflict, setConflict] = useState<string | null>(null);
  const [rejected, setRejected] = useState<AnalysisReport | null>(null);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState('');
  const [focusTargetId, setFocusTargetId] = useState<string | null>(null);
  // Al recargar tras un conflicto se reinician los editores con texto viejo.
  const [reloads, setReloads] = useState(0);

  const view = rulesQuery.data;

  useEffect(() => {
    if (!focusTargetId) {
      return;
    }
    const target = document.getElementById(focusTargetId);
    if (target) {
      target.focus();
      setFocusTargetId(null);
    } else if (!rulesQuery.isFetching) {
      // La fila no está en la página elegida: no hay a dónde llevar el foco.
      setFocusTargetId(null);
    }
  }, [focusTargetId, view, rulesQuery.isFetching]);

  /** Aplica una edición con la versión leída y atiende 409 y 422 de forma común. */
  async function runEdit(
    action: (version: number) => Promise<RulesEditResult>,
    announcement: string,
  ): Promise<EditOutcome> {
    if (!view) {
      return { status: 'handled' };
    }
    setRejected(null);
    setConflict(null);
    setGeneralError(null);
    setStatusMessage('');
    try {
      // La versión se lee de la caché al enviar: la del render puede ser de antes del último guardado.
      const sentVersion =
        queryClient.getQueryData<RulesEditorView>(queryKey)?.version ?? view.version ?? 0;
      const result = await action(sentVersion);
      queryClient.setQueryData<RulesEditorView>(queryKey, (current) =>
        current
          ? {
              ...current,
              version: result.version ?? current.version,
              report: result.report ?? current.report,
              caseResults: result.caseResults ?? current.caseResults,
            }
          : current,
      );
      void queryClient.invalidateQueries({ queryKey });
      // El simulador trae las mismas reglas con su versión: sin releerlo, no dejaría guardar casos.
      void queryClient.invalidateQueries({ queryKey: simulatorQueryKey(encuesta, year) });
      setStatusMessage(announcement);
      return { status: 'ok' };
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409) {
        setConflict(caught.detail);
        return { status: 'handled' };
      }
      if (caught instanceof ApiError && caught.status === 422 && caught.report) {
        const added = newErrorsOf(caught.report, view.report);
        if (added.errors?.length) {
          setRejected(added);
          return { status: 'rejected' };
        }
      }
      return { status: 'error', message: errorMessage(caught) };
    }
  }

  /** Un enlace del análisis cambia a la página del elemento y enfoca su fila. */
  function locate(element: string) {
    const separator = element.indexOf(':');
    const kind = element.slice(0, separator);
    const id = element.slice(separator + 1);
    if (kind === 'regla') {
      const rule = skips.find((item) => item.id === id);
      if (rule) {
        setChosenPage(pageOfRule(rule));
        setFocusTargetId(`regla-${id}`);
      }
    } else if (kind === 'pregunta' || kind === 'validacion') {
      const pageCode = lookup.get(id)?.pageCode;
      if (pageCode) {
        setChosenPage(pageCode);
        setFocusTargetId(`pregunta-${id}`);
      }
    }
  }

  function reload() {
    setConflict(null);
    setRejected(null);
    setBuilder(null);
    setToDelete(null);
    setReloads((count) => count + 1);
    void rulesQuery.refetch();
  }

  async function submitRule(rule: SkipRule) {
    setBuilderError(null);
    setSaving(true);
    let outcome: EditOutcome;
    try {
      outcome = await runEdit(
        (version) => saveSkip(encuesta, year, rule, version),
        'Regla guardada.',
      );
    } finally {
      setSaving(false);
    }
    if (outcome.status === 'error') {
      setBuilderError(outcome.message);
      return;
    }
    if (outcome.status === 'rejected') {
      return;
    }
    setBuilder(null);
    if (outcome.status === 'ok') {
      setFocusTargetId(`regla-${rule.id}`);
    }
  }

  async function confirmDelete() {
    if (!toDelete) {
      return;
    }
    setDeleting(true);
    try {
      const outcome = await runEdit(
        (version) => deleteSkip(encuesta, year, toDelete.id ?? '', version),
        'Regla eliminada.',
      );
      if (outcome.status === 'error') {
        setGeneralError(outcome.message);
      }
      if (outcome.status === 'ok') {
        // La fila desaparece con el botón enfocado: el foco pasa al título de la lista.
        setFocusTargetId('saltos-titulo');
      }
      setToDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  async function submitValidation(column: string, validation: FieldValidation) {
    const outcome = await runEdit(
      (version) => saveValidation(encuesta, year, column, validation, version),
      'Validación guardada.',
    );
    if (outcome.status === 'error') {
      setGeneralError(outcome.message);
    }
  }

  async function removeValidation(column: string) {
    const outcome = await runEdit(
      (version) => deleteValidation(encuesta, year, column, version),
      'Validación eliminada.',
    );
    if (outcome.status === 'error') {
      setGeneralError(outcome.message);
    } else {
      setFocusTargetId(`pregunta-${column}`);
    }
  }

  async function importFile(rules: RuleSet): Promise<string | null> {
    const outcome = await runEdit(
      (version) => importRules(encuesta, year, rules, version),
      'Reglas importadas.',
    );
    return outcome.status === 'error' ? outcome.message : null;
  }

  const questions: RuleQuestion[] = view?.questions ?? [];
  const lookup = new Map(questions.map((question) => [question.column ?? '', question]));
  const pages = [...new Set(questions.map((question) => question.pageCode ?? ''))].filter(Boolean);
  const pageChoices = [...pages, NO_PAGE];
  const selectedPage =
    chosenPage && pageChoices.includes(chosenPage) ? chosenPage : (pages[0] ?? NO_PAGE);
  const withoutPage = selectedPage === NO_PAGE;
  const pageQuestions = questions.filter((question) => question.pageCode === selectedPage);
  const skips = view?.skips ?? [];
  // Una regla es de la página de su primer disparador conocido o, si no hay, de su primer destino.
  const pageOfRule = (rule: SkipRule): string => {
    const known = (columns: string[]) => columns.find((column) => lookup.has(column));
    const column = known(columnsOfCondition(rule.si)) ?? known(targetsOf(rule));
    return (column && lookup.get(column)?.pageCode) || NO_PAGE;
  };
  const pageRules = skips.filter((rule) => pageOfRule(rule) === selectedPage);
  const hiddenOnPage = pageQuestions
    .map((question) => ({
      question,
      rules: skips.filter((rule) => targetsOf(rule).includes(question.column ?? '')),
    }))
    .filter((item) => item.rules.length > 0);
  const legacyIds = new Set(view?.legacySkipIds ?? []);
  const editable = view?.state === 'borrador';

  return (
    <section aria-labelledby="titulo-reglas">
      <Link
        to="/admin/cuestionario"
        className="inline-flex min-h-11 items-center text-primary underline"
      >
        Volver a los años
      </Link>
      <h1 id="titulo-reglas" className="text-2xl font-semibold">
        {title}
      </h1>
      <QuestionnaireTabs survey={encuesta} year={year} />

      {rulesQuery.isPending && (
        <p role="status" aria-live="polite" className="mt-6 text-ink-muted">
          Cargando reglas…
        </p>
      )}
      {rulesQuery.isError && (
        <ErrorState
          message={errorMessage(rulesQuery.error)}
          onRetry={() => void rulesQuery.refetch()}
        />
      )}

      <p role="status" aria-live="polite" className="sr-only">
        {statusMessage}
      </p>

      {conflict && (
        <ConflictNotice
          message={conflict}
          onReload={reload}
          // Con un diálogo abierto el foco espera: al cerrarse, el aviso lo recibe.
          focusOnShow={!builder && !toDelete}
        />
      )}
      {rejected && (
        <AnalysisSummary
          report={rejected}
          survey={encuesta}
          year={year}
          rejected
          focusAlert={!builder && !toDelete}
          onLocate={locate}
        />
      )}
      {generalError && (
        <p
          role="alert"
          className="mt-6 rounded-xl border border-danger/40 bg-surface p-4 text-danger"
        >
          {generalError}
        </p>
      )}

      {view && (
        <div key={reloads}>
          <p className="mt-2 text-ink-muted">
            Estado: {STATE_LABELS[view.state ?? ''] ?? view.state} ·{' '}
            <span>Versión {view.version}</span>
          </p>
          {!editable && (
            <p className="mt-4 rounded-xl border border-ink-muted/30 bg-surface p-4">
              El cuestionario de {view.year} está publicado y es de solo lectura.
            </p>
          )}

          <div className="mt-6 max-w-xs">
            <label htmlFor="pagina-reglas" className="block text-sm font-medium">
              Página
            </label>
            <select
              id="pagina-reglas"
              value={selectedPage}
              onChange={(event) => setChosenPage(event.target.value)}
              className="mt-1 block min-h-11 w-full rounded-lg border border-ink-muted/40 bg-surface px-3 text-base text-ink"
            >
              {pages.map((pageCode) => (
                <option key={pageCode} value={pageCode}>
                  {pageCode}
                </option>
              ))}
              <option value={NO_PAGE}>Reglas sin página</option>
            </select>
          </div>

          {!withoutPage && (
            <section aria-labelledby="titulo-efecto" className="mt-8">
              <h2 id="titulo-efecto" className="text-xl font-semibold">
                Efecto en la página
              </h2>
              {hiddenOnPage.length === 0 ? (
                <p className="mt-2">Ninguna regla oculta preguntas de esta página.</p>
              ) : (
                <ul className="mt-2 grid gap-2">
                  {hiddenOnPage.map(({ question, rules }) => (
                    <li key={question.column}>
                      <strong>{question.column}</strong> se oculta con:
                      <ul className="ml-6 list-disc">
                        {rules.map((rule) => (
                          <li key={rule.id}>
                            <code>{rule.id}</code>: {describeRule(rule, lookup)}
                          </li>
                        ))}
                      </ul>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}

          <section aria-labelledby="saltos-titulo" className="mt-8">
            <h2 id="saltos-titulo" tabIndex={-1} className="text-xl font-semibold">
              Saltos de la página
            </h2>
            <Button
              className="mt-3"
              disabled={!editable}
              onClick={() => {
                setBuilderError(null);
                setBuilder({});
              }}
            >
              Nueva regla
            </Button>
            {pageRules.length === 0 ? (
              <p className="mt-3">Esta página no tiene reglas de salto.</p>
            ) : (
              <ul className="mt-3 grid gap-3">
                {pageRules.map((rule) => {
                  const ruleId = rule.id ?? '';
                  const legacy = legacyIds.has(ruleId);
                  return (
                    <li
                      key={ruleId}
                      id={`regla-${ruleId}`}
                      tabIndex={-1}
                      className="rounded-xl border border-ink-muted/30 p-4"
                    >
                      <p className="font-medium">
                        <code>{ruleId}</code>
                      </p>
                      <p>{describeRule(rule, lookup)}</p>
                      {rule.descripcion && <p className="text-ink-muted">{rule.descripcion}</p>}
                      {legacy ? (
                        <p className="mt-2 text-ink-muted">Heredada, solo lectura</p>
                      ) : (
                        <div className="mt-3 flex flex-wrap gap-3">
                          <Button
                            variant="secondary"
                            aria-label={`Editar regla ${ruleId}`}
                            disabled={!editable}
                            onClick={() => {
                              setBuilderError(null);
                              setBuilder({ rule });
                            }}
                          >
                            Editar
                          </Button>
                          <Button
                            variant="secondary"
                            aria-label={`Eliminar regla ${ruleId}`}
                            disabled={!editable}
                            onClick={() => setToDelete(rule)}
                          >
                            Eliminar
                          </Button>
                        </div>
                      )}
                      <FindingNotes
                        findings={findingsOfElements(view.report, [`regla:${ruleId}`])}
                      />
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          {!withoutPage && (
            <section aria-labelledby="validaciones-titulo" className="mt-8">
              <h2 id="validaciones-titulo" className="text-xl font-semibold">
                Validaciones de la página
              </h2>
              <ValidationEditor
                questions={pageQuestions}
                validations={view.validations ?? {}}
                report={view.report}
                disabled={!editable}
                onSave={submitValidation}
                onDelete={removeValidation}
              />
            </section>
          )}

          <section aria-labelledby="analisis-titulo" className="mt-8">
            <h2 id="analisis-titulo" className="text-xl font-semibold">
              Análisis del cuestionario
            </h2>
            <AnalysisSummary
              report={view.report ?? {}}
              survey={encuesta}
              year={year}
              onLocate={locate}
            />
          </section>

          <RulesJsonPanel
            survey={encuesta}
            year={year}
            readOnly={!editable}
            onImport={importFile}
          />

          {builder && (
            <SkipRuleBuilder
              rule={builder.rule}
              questions={questions}
              existingIds={skips.map((rule) => rule.id ?? '')}
              busy={saving}
              error={builderError}
              rejected={
                rejected && (
                  <AnalysisSummary
                    report={rejected}
                    survey={encuesta}
                    year={year}
                    rejected
                    withLinks={false}
                  />
                )
              }
              onSave={(rule) => void submitRule(rule)}
              onCancel={() => {
                // El diálogo devuelve el foco al botón que lo abrió.
                setBuilder(null);
                setRejected(null);
              }}
            />
          )}
          {toDelete && (
            <ConfirmDialog
              open
              title={`Eliminar la regla ${toDelete.id}`}
              confirmLabel="Eliminar"
              cancelLabel="Cancelar"
              busy={deleting}
              onCancel={() => setToDelete(null)}
              onConfirm={() => void confirmDelete()}
            >
              <p>{describeRule(toDelete, lookup)}</p>
              <p className="mt-2">Esta acción no se puede deshacer.</p>
            </ConfirmDialog>
          )}
        </div>
      )}
    </section>
  );
}
