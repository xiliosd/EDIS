import { evaluate, explain } from '../survey/rules/rule-evaluator';
import type { QuestionnaireEntry } from '../survey/survey-api';
import type { SimulatorView, TestCase } from '../admin-rules/rules-api';

/** Respuestas de todas las páginas por columna; viven solo en memoria, nunca en localStorage. */
export type SimulatorAnswers = Record<string, string>;

export interface PageSimulation {
  /** Pregunta oculta → ids de las reglas que la ocultan (ronda estable del punto fijo). */
  hidden: Record<string, string[]>;
  /** Código de error por pregunta activa de la página (mismo alcance que CaseRunner). */
  errors: Record<string, string>;
}

export function isActiveItem(entry: QuestionnaireEntry): boolean {
  return entry.kind === 'ITEM' && entry.answer?.columnStatus === 'ACTIVE';
}

export function columnOf(entry: QuestionnaireEntry): string {
  return entry.answer?.columnCode ?? '';
}

/** Columnas que son ítems activos de la página, en el orden del cuestionario. */
export function pageColumns(view: SimulatorView, pageCode: string): string[] {
  const page = (view.pages ?? []).find((candidate) => candidate.pageCode === pageCode);
  return (page?.entries ?? []).filter(isActiveItem).map(columnOf);
}

function activeColumns(view: SimulatorView): Set<string> {
  return new Set(
    (view.pages ?? []).flatMap((page) => (page.entries ?? []).filter(isActiveItem).map(columnOf)),
  );
}

/**
 * Evalúa las reglas con las respuestas de todas las páginas, igual que el servidor: las ocultas
 * son las de cualquier pregunta activa del año y los errores solo los de la página elegida.
 */
export function simulatePage(
  view: SimulatorView,
  pageCode: string,
  answers: SimulatorAnswers,
): PageSimulation {
  const rules = view.rules ?? {};
  const active = activeColumns(view);
  const evaluation = evaluate(rules, answers, pageColumns(view, pageCode));
  const reasons = explain(rules, answers);
  const hidden = Object.fromEntries(
    evaluation.disabled
      .filter((column) => active.has(column))
      .map((column) => [column, reasons[column] ?? []]),
  );
  return { hidden, errors: evaluation.errors };
}

/** Caso de prueba con lo que el simulador mostró: es lo que el servidor debe reproducir. */
export function caseFromSimulation(
  id: string,
  name: string,
  pageCode: string,
  answers: SimulatorAnswers,
  result: PageSimulation,
  view: SimulatorView,
): TestCase {
  const active = activeColumns(view);
  const recorded = Object.fromEntries(
    Object.entries(answers).filter(([column, value]) => active.has(column) && value.trim() !== ''),
  );
  return {
    id,
    nombre: name,
    pagina: pageCode,
    respuestas: recorded,
    esperado: { ocultas: Object.keys(result.hidden).sort(), errores: result.errors },
  };
}
