import { useCallback, useEffect, useId, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ConflictNotice } from '../../components/ConflictNotice';
import { CorrectionReasonBar } from '../../components/CorrectionReasonBar';
import { ErrorState } from '../../components/ErrorState';
import { QuestionnaireTabs } from '../../components/QuestionnaireTabs';
import { useCorrectionReason } from '../../hooks/use-correction-reason';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { ApiError } from '../../services/api-error';
import { errorMessage, REASON_REQUIRED_NOTE } from '../admin-questions/tree-editing';
import { describeRule } from '../admin-rules/rule-description';
import {
  deleteCase,
  fetchRules,
  fetchSimulator,
  rulesQueryKey,
  saveCase,
  simulatorQueryKey,
  type RuleQuestion,
  type RulesEditorView,
  type RulesEditResult,
  type SimulatorView,
  type TestCase,
} from '../admin-rules/rules-api';
import { messageFor } from '../survey/answer-error-messages';
import { QuestionField, type PageValues } from '../survey/questions/QuestionField';
import type { PageDetail, QuestionnaireEntry } from '../survey/survey-api';
import { CaseList } from './CaseList';
import { SaveCaseDialog } from './SaveCaseDialog';
import {
  caseFromSimulation,
  columnOf,
  isActiveItem,
  simulatePage,
  type PageSimulation,
  type SimulatorAnswers,
} from './simulator-state';

function itemLabel(entry: QuestionnaireEntry): string {
  return `${entry.label ?? ''} ${entry.text ?? ''}`.trim();
}

function defaultValue(entry: QuestionnaireEntry, initial: SimulatorAnswers): string {
  // Como en la encuesta, una casilla sin marcar vale "0" y no queda vacía.
  return initial[columnOf(entry)] ?? (entry.answer?.inputType === 'CHECKBOX' ? '0' : '');
}

interface SimulatedPageProps {
  page: PageDetail;
  initialAnswers: SimulatorAnswers;
  simulation: PageSimulation;
  view: SimulatorView;
  questions: Map<string, RuleQuestion>;
  onAnswersChange: (values: PageValues) => void;
}

/** Una página con los mismos campos de la encuesta, sin guardar nada del encuestado. */
function SimulatedPage({
  page,
  initialAnswers,
  simulation,
  view,
  questions,
  onAnswersChange,
}: SimulatedPageProps) {
  const noteBaseId = useId();
  const entries = page.entries ?? [];
  const form = useForm<PageValues>({
    defaultValues: Object.fromEntries(
      entries
        .filter(isActiveItem)
        .map((entry) => [columnOf(entry), defaultValue(entry, initialAnswers)]),
    ),
  });
  const valuesKey = JSON.stringify(form.watch());
  useEffect(() => {
    onAnswersChange(form.getValues());
    // valuesKey cambia con cada respuesta: es lo que lleva los valores a las respuestas del simulador.
  }, [valuesKey, form, onAnswersChange]);

  function noteOf(column: string): string[] {
    return (simulation.hidden[column] ?? []).map((ruleId) => {
      const rule = (view.rules?.saltos ?? []).find((candidate) => candidate.id === ruleId);
      return rule
        ? `Oculta por la regla ${ruleId}: ${describeRule(rule, questions)}`
        : `Oculta por la regla ${ruleId}.`;
    });
  }

  return (
    <form noValidate onSubmit={(event) => event.preventDefault()} className="mt-4 grid gap-6">
      {entries.map((entry) => {
        const column = columnOf(entry);
        const code = simulation.errors[column];
        const field = (
          <QuestionField
            entry={entry}
            column={column}
            label={itemLabel(entry)}
            error={code ? messageFor(code) : undefined}
            register={form.register}
            control={form.control}
            rules={view.rules}
          />
        );
        const noteId = `${noteBaseId}-${column}`;
        return (
          <div key={entry.id}>
            {(entry.headers ?? []).map((header, index) =>
              header.kind === 'HEADING' ? (
                <h3 key={index} className="mb-2 text-lg font-semibold">
                  {header.text}
                </h3>
              ) : (
                <h4 key={index} className="mb-2 font-medium">
                  {header.text}
                </h4>
              ),
            )}
            {entry.kind === 'STATEMENT' && <p className="font-semibold">{itemLabel(entry)}</p>}
            {isActiveItem(entry) &&
              (column in simulation.hidden ? (
                // Oculta pero visible: el administrador ve qué dejaría de ver el encuestado y por qué.
                <fieldset
                  disabled
                  aria-describedby={noteId}
                  className="m-0 min-w-0 rounded-lg border border-dashed border-ink-muted/40 p-3 text-ink-muted"
                >
                  <legend className="sr-only">{itemLabel(entry)} (oculta)</legend>
                  {field}
                  <div id={noteId} className="mt-2 text-sm">
                    {noteOf(column).map((note) => (
                      <p key={note}>{note}</p>
                    ))}
                  </div>
                </fieldset>
              ) : (
                field
              ))}
          </div>
        );
      })}
    </form>
  );
}

interface WorkspaceProps {
  survey: string;
  year: number;
  view: SimulatorView;
  rulesView: RulesEditorView;
  onReload: () => void;
}

/** Simulador y casos de un año ya cargado: las respuestas viven solo en memoria. */
function SimulatorWorkspace({ survey, year, view, rulesView, onReload }: WorkspaceProps) {
  const queryClient = useQueryClient();
  const { hash } = useLocation();
  const pages = view.pages ?? [];
  const [chosenPage, setChosenPage] = useState<string | null>(null);
  const [answers, setAnswers] = useState<SimulatorAnswers>({});
  // Reiniciar o abrir un caso vuelve a montar la página con las respuestas nuevas.
  const [formKey, setFormKey] = useState(0);
  const [focusRequest, setFocusRequest] = useState(0);
  const [saveOpen, setSaveOpen] = useState(false);
  const [saveBusy, setSaveBusy] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const [rulesChanged, setRulesChanged] = useState(false);
  // Si el guardado no se completa (reglas cambiadas o 409), el id y el nombre esperan al reintento.
  const [pendingDraft, setPendingDraft] = useState<{ id: string; name: string } | null>(null);
  const [statusMessage, setStatusMessage] = useState('');

  const pageCode =
    chosenPage && pages.some((page) => page.pageCode === chosenPage)
      ? chosenPage
      : (pages[0]?.pageCode ?? '');
  const page = pages.find((candidate) => candidate.pageCode === pageCode);
  const simulation = useMemo(
    () => simulatePage(view, pageCode, answers),
    [view, pageCode, answers],
  );
  const questions = useMemo(
    () => new Map((rulesView.questions ?? []).map((question) => [question.column ?? '', question])),
    [rulesView.questions],
  );
  const cases = rulesView.cases ?? [];
  const reason = useCorrectionReason();
  const correction = rulesView.state === 'publicado';
  // Un año publicado admite guardar y eliminar casos como corrección, solo con motivo válido.
  const editable = rulesView.state === 'borrador' || (correction && reason.motivo !== undefined);
  const rulesKey = rulesQueryKey(survey, year);
  const simulatorKey = simulatorQueryKey(survey, year);

  /** La versión vigente según la caché al enviar, no la del render (puede ser anterior). */
  function currentRulesVersion(): number {
    return queryClient.getQueryData<RulesEditorView>(rulesKey)?.version ?? rulesView.version ?? 0;
  }

  const handleAnswersChange = useCallback((values: PageValues) => {
    setAnswers((current) => {
      const changed = Object.entries(values).some(([column, value]) => current[column] !== value);
      return changed ? { ...current, ...values } : current;
    });
  }, []);

  useEffect(() => {
    if (focusRequest > 0) {
      document.getElementById('simulador-pagina')?.focus();
    }
  }, [focusRequest]);

  // Los enlaces del análisis llegan con #caso-<id>: se desplaza la vista y se enfoca la fila.
  useEffect(() => {
    if (!hash.startsWith('#caso-')) {
      return;
    }
    const row = document.getElementById(hash.slice(1));
    if (row) {
      row.scrollIntoView?.({ block: 'center' });
      row.focus();
    }
  }, [hash]);

  function applyResult(result: RulesEditResult, update: (current: TestCase[]) => TestCase[]) {
    queryClient.setQueryData<RulesEditorView>(rulesKey, (current) =>
      current
        ? {
            ...current,
            version: result.version ?? current.version,
            report: result.report ?? current.report,
            caseResults: result.caseResults ?? current.caseResults,
            cases: update(current.cases ?? []),
          }
        : current,
    );
    // Un caso no cambia las reglas: el simulador sigue valiendo con la versión nueva del archivo.
    queryClient.setQueryData<SimulatorView>(simulatorKey, (current) =>
      current ? { ...current, version: result.version ?? current.version } : current,
    );
  }

  async function saveCurrentCase(draft: { id: string; name: string }) {
    const rulesVersion = currentRulesVersion();
    const simulatorVersion = queryClient.getQueryData<SimulatorView>(simulatorKey)?.version;
    setRulesChanged(false);
    // El resultado esperado sale del simulador: si sus reglas no son las vigentes, no se guarda.
    if ((simulatorVersion ?? view.version) !== rulesVersion) {
      setPendingDraft(draft);
      setSaveOpen(false);
      setRulesChanged(true);
      void queryClient.invalidateQueries({ queryKey: simulatorKey });
      void queryClient.invalidateQueries({ queryKey: rulesKey });
      return;
    }
    const testCase = caseFromSimulation(draft.id, draft.name, pageCode, answers, simulation, view);
    setSaveBusy(true);
    setSaveError(null);
    setConflict(null);
    try {
      const result = await saveCase(
        survey,
        year,
        testCase,
        rulesVersion,
        correction ? reason.motivo : undefined,
      );
      if (correction) {
        reason.clear();
      }
      applyResult(result, (current) =>
        current.some((item) => item.id === testCase.id)
          ? current.map((item) => (item.id === testCase.id ? testCase : item))
          : [...current, testCase],
      );
      setSaveOpen(false);
      setPendingDraft(null);
      setStatusMessage(`Caso ${draft.id} guardado.`);
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409) {
        setPendingDraft(draft);
        setConflict(caught.detail);
        setSaveOpen(false);
      } else {
        setSaveError(errorMessage(caught));
      }
    } finally {
      setSaveBusy(false);
    }
  }

  async function removeCase(testCase: TestCase) {
    const caseId = testCase.id ?? '';
    setDeleteError(null);
    setConflict(null);
    try {
      const result = await deleteCase(
        survey,
        year,
        caseId,
        currentRulesVersion(),
        correction ? reason.motivo : undefined,
      );
      if (correction) {
        reason.clear();
      }
      applyResult(result, (current) => current.filter((item) => item.id !== caseId));
      setStatusMessage(`Caso ${caseId} eliminado.`);
      // La fila desaparece con el botón enfocado: el foco pasa al título de la lista.
      document.getElementById('casos-titulo')?.focus();
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409) {
        setConflict(caught.detail);
      } else {
        setDeleteError(errorMessage(caught));
      }
    }
  }

  function openCase(testCase: TestCase) {
    if (testCase.pagina && pages.some((candidate) => candidate.pageCode === testCase.pagina)) {
      setChosenPage(testCase.pagina);
    }
    setAnswers({ ...testCase.respuestas });
    // El borrador era de otras respuestas: con otro caso ya no aplica.
    setPendingDraft(null);
    setFormKey((count) => count + 1);
    setFocusRequest((count) => count + 1);
    setStatusMessage(`Caso ${testCase.id} cargado en el simulador.`);
  }

  function resetAnswers() {
    setAnswers({});
    setPendingDraft(null);
    setFormKey((count) => count + 1);
    setStatusMessage('Respuestas reiniciadas.');
  }

  return (
    <>
      <p role="status" aria-live="polite" className="sr-only">
        {statusMessage}
      </p>
      {conflict && (
        <ConflictNotice message={conflict} onReload={onReload} focusOnShow={!saveOpen} />
      )}
      {rulesChanged && (
        <p role="alert" className="mt-4 rounded-xl border border-danger/40 bg-surface p-4">
          Las reglas cambiaron; revise el resultado antes de guardar.
        </p>
      )}
      {correction && (
        <>
          <CorrectionReasonBar value={reason.text} onChange={reason.setText} />
          {!editable && <p className="mt-2 text-ink-muted">{REASON_REQUIRED_NOTE}</p>}
        </>
      )}

      <div className="mt-6 max-w-xs">
        <label htmlFor="pagina-simulador" className="block text-sm font-medium">
          Página
        </label>
        <select
          id="pagina-simulador"
          value={pageCode}
          onChange={(event) => setChosenPage(event.target.value)}
          className="mt-1 block min-h-11 w-full rounded-lg border border-ink-muted/40 bg-surface px-3 text-base text-ink"
        >
          {pages.map((item) => (
            <option key={item.pageCode} value={item.pageCode}>
              {item.pageCode}
            </option>
          ))}
        </select>
      </div>

      <section aria-labelledby="simulador-pagina" className="mt-6">
        <h2 id="simulador-pagina" tabIndex={-1} className="text-xl font-semibold">
          Página {pageCode}
        </h2>
        {page ? (
          <SimulatedPage
            key={`${pageCode}-${formKey}`}
            page={page}
            initialAnswers={answers}
            simulation={simulation}
            view={view}
            questions={questions}
            onAnswersChange={handleAnswersChange}
          />
        ) : (
          <p className="mt-3">Este cuestionario no tiene páginas.</p>
        )}
        <div className="mt-6 flex flex-wrap gap-3">
          <Button variant="secondary" onClick={resetAnswers}>
            Reiniciar respuestas
          </Button>
          <Button
            disabled={!editable || !page}
            onClick={() => {
              setSaveError(null);
              setSaveOpen(true);
            }}
          >
            Guardar como caso de prueba
          </Button>
        </div>
      </section>

      <section aria-labelledby="casos-titulo" className="mt-10">
        <h2 id="casos-titulo" tabIndex={-1} className="text-xl font-semibold">
          Casos de prueba
        </h2>
        {deleteError && (
          <p
            role="alert"
            className="mt-3 rounded-xl border border-danger/40 bg-surface p-4 text-danger"
          >
            {deleteError}
          </p>
        )}
        <CaseList
          cases={cases}
          results={rulesView.caseResults ?? []}
          editable={editable}
          onOpen={openCase}
          onDelete={(testCase) => void removeCase(testCase)}
        />
      </section>

      {saveOpen && (
        <SaveCaseDialog
          existingIds={cases.map((item) => item.id ?? '')}
          busy={saveBusy}
          error={saveError}
          initialDraft={pendingDraft}
          onSave={(draft) => void saveCurrentCase(draft)}
          onCancel={() => {
            setPendingDraft(null);
            setSaveOpen(false);
          }}
        />
      )}
    </>
  );
}

/** Simulador del cuestionario de un año: recorre las páginas con las reglas y guarda casos. */
export function SimulatorPage() {
  const { encuesta = '', anio = '' } = useParams();
  const year = Number(anio);
  const title = `Simulador ${encuesta} ${anio}`;
  useDocumentTitle(title);
  const simulator = useQuery({
    queryKey: simulatorQueryKey(encuesta, year),
    queryFn: () => fetchSimulator(encuesta, year),
  });
  const rules = useQuery({
    queryKey: rulesQueryKey(encuesta, year),
    queryFn: () => fetchRules(encuesta, year),
  });

  function reload() {
    void simulator.refetch();
    void rules.refetch();
  }

  return (
    <section aria-labelledby="titulo-simulador">
      <Link
        to="/admin/cuestionario"
        className="inline-flex min-h-11 items-center text-primary underline"
      >
        Volver a los años
      </Link>
      <h1 id="titulo-simulador" className="text-2xl font-semibold">
        {title}
      </h1>
      <QuestionnaireTabs survey={encuesta} year={year} />

      {(simulator.isPending || rules.isPending) && !simulator.isError && !rules.isError && (
        <p role="status" aria-live="polite" className="mt-6 text-ink-muted">
          Cargando el simulador…
        </p>
      )}
      {(simulator.isError || rules.isError) && (
        <ErrorState message={errorMessage(simulator.error ?? rules.error)} onRetry={reload} />
      )}
      {simulator.data && rules.data && (
        <SimulatorWorkspace
          survey={encuesta}
          year={year}
          view={simulator.data}
          rulesView={rules.data}
          onReload={reload}
        />
      )}
    </section>
  );
}
