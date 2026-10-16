import type { components } from '../../../services/api-schema';

export type RuleSet = components['schemas']['RuleSet'];
type Condition = components['schemas']['Condition'];
type FieldValidation = components['schemas']['FieldValidation'];

export interface Evaluation {
  disabled: string[];
  errors: Record<string, string>;
}

const INTEGER = /^-?\d+$/;
const DECIMAL = /^-?\d+(\.\d+)?$/;

function isBlank(value: string | null | undefined): boolean {
  return value == null || value.trim() === '';
}

function toNumber(value: string | null | undefined): number | undefined {
  if (value == null || !DECIMAL.test(value.trim())) {
    return undefined;
  }
  return Number(value.trim());
}

function applies(condition: Condition, rawValue: string | undefined): boolean {
  const value = isBlank(rawValue) ? '' : (rawValue ?? '').trim();
  const values = condition.valores ?? [];
  const left = toNumber(value);
  const right = toNumber(condition.valor);
  switch (condition.op) {
    case 'eq':
      return value === condition.valor;
    case 'ne':
      return value !== '' && value !== condition.valor;
    case 'in':
      return values.includes(value);
    case 'notIn':
      return value !== '' && !values.includes(value);
    case 'gt':
      return left !== undefined && right !== undefined && left > right;
    case 'lt':
      return left !== undefined && right !== undefined && left < right;
    case 'empty':
      return value === '';
    case 'notEmpty':
      return value !== '';
    default:
      return false;
  }
}

function matches(condition: Condition, answers: Record<string, string | undefined>): boolean {
  if (condition.y) {
    return condition.y.every((child) => matches(child, answers));
  }
  if (condition.o) {
    return condition.o.some((child) => matches(child, answers));
  }
  const test = (column: string) => applies(condition, answers[column]);
  if (condition.pregunta) {
    return test(condition.pregunta);
  }
  if (condition.todas) {
    return condition.todas.every(test);
  }
  return condition.alguna ? condition.alguna.some(test) : false;
}

function validate(rawValue: string | undefined, validation?: FieldValidation): string | undefined {
  if (isBlank(rawValue)) {
    const required = validation?.obligatoria ?? true;
    return required ? 'REQUERIDA' : undefined;
  }
  if (!validation) {
    return undefined;
  }
  const value = (rawValue ?? '').trim();
  if (
    (validation.tipo === 'entero' && !INTEGER.test(value)) ||
    (validation.tipo === 'decimal' && !DECIMAL.test(value))
  ) {
    return 'TIPO_INVALIDO';
  }
  if (validation.min != null || validation.max != null) {
    const number = toNumber(value);
    if (number === undefined) {
      return 'TIPO_INVALIDO';
    }
    if (
      (validation.min != null && number < validation.min) ||
      (validation.max != null && number > validation.max)
    ) {
      return 'FUERA_DE_RANGO';
    }
  }
  if (validation.longitudMax != null && value.length > validation.longitudMax) {
    return 'LONGITUD';
  }
  return undefined;
}

function sameSet(left: Set<string>, right: Set<string>): boolean {
  return left.size === right.size && [...left].every((item) => right.has(item));
}

/** Espejo de RuleEvaluator.java (spec §5.2): mismo punto fijo, mismos operadores y códigos. */
export function evaluate(
  rules: RuleSet,
  answers: Record<string, string | undefined>,
  itemColumns: string[],
): Evaluation {
  const skips = rules.saltos ?? [];
  let disabled = new Set<string>();
  for (let round = 0; round <= skips.length; round += 1) {
    const effective = { ...answers };
    disabled.forEach((column) => delete effective[column]);
    const next = new Set<string>();
    for (const rule of skips) {
      if (rule.si && matches(rule.si, effective)) {
        (rule.entonces?.preguntas ?? []).forEach((column) => next.add(column));
      }
    }
    if (sameSet(next, disabled)) {
      break;
    }
    disabled = next;
  }
  const errors: Record<string, string> = {};
  for (const column of itemColumns) {
    if (disabled.has(column)) {
      continue;
    }
    const code = validate(answers[column], rules.validaciones?.[column]);
    if (code) {
      errors[column] = code;
    }
  }
  return { disabled: [...disabled].sort(), errors };
}
