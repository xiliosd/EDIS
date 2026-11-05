import { describeRule } from '../admin-rules/rule-description';
import type { RuleSet } from '../admin-rules/rules-api';

export interface Change {
  id: string;
  kind: 'agregada' | 'eliminada' | 'cambiada';
  before?: string;
  after?: string;
}

export interface VersionDiff {
  skips: Change[];
  validations: Change[];
  cases: Change[];
  tables: Change[];
}

/** JSON con las claves ordenadas: dos contenidos iguales dan el mismo texto sin importar el orden. */
export function stableJson(value: unknown): string {
  return JSON.stringify(sortKeys(value));
}

function sortKeys(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(sortKeys);
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, child]) => [key, sortKeys(child)]),
    );
  }
  return value;
}

/** Compara dos mapas por clave; `show` da el texto que ve la persona de cada elemento. */
function compareByKey<T>(
  older: Map<string, T>,
  newer: Map<string, T>,
  show: (item: T) => string,
): Change[] {
  const ids = [...new Set([...older.keys(), ...newer.keys()])].sort((a, b) => a.localeCompare(b));
  const changes: Change[] = [];
  for (const id of ids) {
    const before = older.get(id);
    const after = newer.get(id);
    if (before === undefined && after !== undefined) {
      changes.push({ id, kind: 'agregada', after: show(after) });
    } else if (before !== undefined && after === undefined) {
      changes.push({ id, kind: 'eliminada', before: show(before) });
    } else if (before !== undefined && after !== undefined) {
      if (stableJson(before) !== stableJson(after)) {
        changes.push({ id, kind: 'cambiada', before: show(before), after: show(after) });
      }
    }
  }
  return changes;
}

function byId<T extends { id?: string }>(items: T[] | undefined): Map<string, T> {
  return new Map((items ?? []).map((item) => [item.id ?? '', item]));
}

/**
 * Un salto puede cambiar en campos que su descripción no muestra (p. ej. el texto libre):
 * si el texto de antes y después es igual, se muestra el JSON para que el cambio sea visible.
 */
function markInvisibleChanges(
  changes: Change[],
  older: Map<string, unknown>,
  newer: Map<string, unknown>,
): Change[] {
  return changes.map((change) =>
    change.kind === 'cambiada' && change.before === change.after
      ? {
          ...change,
          before: stableJson(older.get(change.id)),
          after: stableJson(newer.get(change.id)),
        }
      : change,
  );
}

/** Diferencias entre dos versiones del archivo de reglas, de la más antigua a la más nueva. */
export function versionDiff(older: RuleSet, newer: RuleSet): VersionDiff {
  // Sin catálogo de preguntas los valores se muestran tal cual, lo que basta para comparar.
  const noQuestions = new Map();
  return {
    skips: markInvisibleChanges(
      compareByKey(byId(older.saltos), byId(newer.saltos), (rule) =>
        describeRule(rule, noQuestions),
      ),
      byId(older.saltos),
      byId(newer.saltos),
    ),
    validations: compareByKey(
      new Map(Object.entries(older.validaciones ?? {})),
      new Map(Object.entries(newer.validaciones ?? {})),
      stableJson,
    ),
    cases: compareByKey(byId(older.casos), byId(newer.casos), stableJson),
    tables: compareByKey(
      new Map(Object.entries(older.tablas ?? {})),
      new Map(Object.entries(newer.tablas ?? {})),
      (table) => table,
    ),
  };
}
