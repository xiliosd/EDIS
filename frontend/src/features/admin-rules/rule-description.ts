import type { components } from '../../services/api-schema';

type Condition = components['schemas']['Condition'];
type SkipRule = components['schemas']['SkipRule'];
type RuleQuestion = components['schemas']['RuleQuestion'];

type Questions = Map<string, RuleQuestion>;

/** El valor se muestra por la etiqueta de la opción; si no hay, tal cual. */
function labelOf(value: string, columns: string[], questions: Questions): string {
  for (const column of columns) {
    const option = questions.get(column)?.options?.find((candidate) => candidate.value === value);
    if (option?.label) {
      return option.label;
    }
  }
  return value;
}

function describeOperator(condition: Condition, columns: string[], questions: Questions): string {
  const label = (value: string | undefined) => labelOf(value ?? '', columns, questions);
  const labels = (condition.valores ?? []).map(label).join(', ');
  switch (condition.op) {
    case 'eq':
      return `= ${label(condition.valor)}`;
    case 'ne':
      return `≠ ${label(condition.valor)}`;
    case 'in':
      return `es una de {${labels}}`;
    case 'notIn':
      return `no es ninguna de {${labels}}`;
    case 'gt':
      return `> ${condition.valor ?? ''}`;
    case 'lt':
      return `< ${condition.valor ?? ''}`;
    case 'empty':
      return 'está vacía';
    case 'notEmpty':
      return 'tiene respuesta';
    default:
      return condition.op ?? '';
  }
}

/** Lee una condición en lenguaje claro, p. ej. `E03 = Sí` o `(A = 1 y B está vacía)`. */
export function describeCondition(condition: Condition, questions: Questions): string {
  if (condition.y) {
    return `(${condition.y.map((child) => describeCondition(child, questions)).join(' y ')})`;
  }
  if (condition.o) {
    return `(${condition.o.map((child) => describeCondition(child, questions)).join(' o ')})`;
  }
  const quantifier = condition.todas ? 'todas de ' : condition.alguna ? 'alguna de ' : '';
  const columns = condition.todas ?? condition.alguna ?? [condition.pregunta ?? ''];
  return `${quantifier}${columns.join(', ')} ${describeOperator(condition, columns, questions)}`;
}

/** Lee una regla de salto, p. ej. `Si E03 = Sí → no se muestra E04`. */
export function describeRule(rule: SkipRule, questions: Questions): string {
  const targets = rule.entonces?.preguntas ?? [];
  if (!rule.si || targets.length === 0) {
    return 'Regla incompleta';
  }
  return `Si ${describeCondition(rule.si, questions)} → no se muestra ${targets.join(', ')}`;
}
