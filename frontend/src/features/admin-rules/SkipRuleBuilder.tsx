import { useState, type ReactNode } from 'react';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { TextField } from '../../components/TextField';
import { describeRule } from './rule-description';
import type { Condition, RuleQuestion, SkipRule } from './rules-api';

interface SkipRuleBuilderProps {
  /** Regla que se edita; sin ella se crea una nueva. */
  rule?: SkipRule;
  questions: RuleQuestion[];
  /** Ids ya usados, para no repetir el de una regla nueva. */
  existingIds: string[];
  busy?: boolean;
  /** Error del servidor que no corresponde a un campo. */
  error?: string | null;
  /** Resumen de errores nuevos del servidor, mostrado dentro del diálogo. */
  rejected?: ReactNode;
  onSave: (rule: SkipRule) => void;
  onCancel: () => void;
}

type Mode = 'una' | 'todas' | 'alguna';
type Kind = 'TEXT' | 'CHECKBOX' | 'CHOICE' | 'NONE';

interface ConditionDraft {
  key: number;
  mode: Mode;
  columns: string[];
  op: string;
  value: string;
  values: string[];
}

interface Draft {
  id: string;
  descripcion: string;
  join: 'y' | 'o';
  conditions: ConditionDraft[];
  targets: string[];
}

const RULE_ID_PATTERN = /^[a-z0-9][a-z0-9-]{0,40}$/;
const SINGLE_VALUE_OPS = new Set(['eq', 'ne', 'gt', 'lt']);
const LIST_VALUE_OPS = new Set(['in', 'notIn']);

const OPERATOR_LABELS: Record<string, string> = {
  eq: 'es igual a',
  ne: 'es distinto de',
  in: 'es una de',
  notIn: 'no es ninguna de',
  gt: 'es mayor que',
  lt: 'es menor que',
  empty: 'está vacía',
  notEmpty: 'tiene respuesta',
};

const OPERATORS_BY_KIND: Record<Kind, string[]> = {
  TEXT: ['eq', 'ne', 'gt', 'lt', 'empty', 'notEmpty'],
  CHECKBOX: ['eq', 'ne', 'empty', 'notEmpty'],
  CHOICE: ['eq', 'ne', 'in', 'notIn', 'empty', 'notEmpty'],
  // Tipo desconocido sin opciones: no hay valores que comparar.
  NONE: ['empty', 'notEmpty'],
};

const CHECKBOX_OPTIONS = [
  { value: '1', label: 'Sí' },
  { value: '0', label: 'No' },
];

const MODE_LABELS: Record<Mode, string> = {
  una: 'Una pregunta',
  todas: 'Todas',
  alguna: 'Alguna',
};

const SELECT_CLASS =
  'mt-1 block min-h-11 w-full rounded-lg border border-ink-muted/40 bg-surface px-3 text-base text-ink aria-invalid:border-danger';

const MAX_LABEL_LENGTH = 70;

function questionLabel(question: RuleQuestion): string {
  const text = question.text ?? '';
  const excerpt = text.length > MAX_LABEL_LENGTH ? `${text.slice(0, MAX_LABEL_LENGTH)}…` : text;
  return `${question.column} — ${excerpt}`;
}

function kindOf(question: RuleQuestion | undefined): Kind {
  if (question?.inputType === 'TEXT') {
    return 'TEXT';
  }
  if (question?.inputType === 'CHECKBOX') {
    return 'CHECKBOX';
  }
  if (!question || question.inputType === 'SINGLE_CHOICE' || question.options?.length) {
    return 'CHOICE';
  }
  // El texto libre es solo para TEXT, igual que en el editor de validaciones.
  return 'NONE';
}

/** Dos preguntas se combinan en todas/alguna solo si tienen el mismo tipo y las mismas opciones. */
function signatureOf(question: RuleQuestion | undefined): string {
  const options = kindOf(question) === 'CHOICE' ? (question?.options ?? []) : [];
  return JSON.stringify([kindOf(question), options.map((item) => [item.value, item.label])]);
}

function valueOptionsOf(question: RuleQuestion | undefined) {
  return kindOf(question) === 'CHECKBOX' ? CHECKBOX_OPTIONS : (question?.options ?? []);
}

function isLeaf(condition: Condition): boolean {
  return !condition.y && !condition.o;
}

/** Una condición o un solo nivel de Y/O sobre condiciones simples; lo demás no se edita aquí. */
export function isFlatCondition(condition: Condition | undefined): boolean {
  if (!condition) {
    return true;
  }
  const children = condition.y ?? condition.o;
  if (condition.y && condition.o) {
    return false;
  }
  return children ? children.length > 0 && children.every(isLeaf) : true;
}

let nextConditionKey = 1;

function emptyCondition(): ConditionDraft {
  return { key: nextConditionKey++, mode: 'una', columns: [], op: 'eq', value: '', values: [] };
}

function draftOfCondition(condition: Condition): ConditionDraft {
  const mode: Mode = condition.todas ? 'todas' : condition.alguna ? 'alguna' : 'una';
  const columns =
    condition.todas ?? condition.alguna ?? (condition.pregunta ? [condition.pregunta] : []);
  return {
    key: nextConditionKey++,
    mode,
    columns,
    op: condition.op ?? 'eq',
    value: condition.valor ?? '',
    values: condition.valores ?? [],
  };
}

function draftOfRule(rule: SkipRule | undefined): Draft {
  const si = rule?.si;
  const group = si?.y ?? si?.o;
  const leaves = group ?? (si ? [si] : [emptyCondition()]);
  return {
    id: rule?.id ?? '',
    descripcion: rule?.descripcion ?? '',
    join: si?.o ? 'o' : 'y',
    conditions: leaves.map((item) => ('key' in item ? item : draftOfCondition(item))),
    targets: rule?.entonces?.preguntas ?? [],
  };
}

function conditionOf(draft: ConditionDraft): Condition {
  const condition: Condition =
    draft.mode === 'una'
      ? { pregunta: draft.columns[0] ?? '' }
      : draft.mode === 'todas'
        ? { todas: draft.columns }
        : { alguna: draft.columns };
  condition.op = draft.op;
  if (SINGLE_VALUE_OPS.has(draft.op)) {
    condition.valor = draft.value;
  }
  if (LIST_VALUE_OPS.has(draft.op)) {
    condition.valores = draft.values;
  }
  return condition;
}

function ruleOf(draft: Draft, existing: SkipRule | undefined): SkipRule {
  const conditions = draft.conditions.map(conditionOf);
  const rule: SkipRule = { id: existing?.id ?? draft.id.trim() };
  if (draft.descripcion.trim()) {
    rule.descripcion = draft.descripcion.trim();
  }
  rule.si = conditions.length === 1 ? conditions[0] : { [draft.join]: conditions };
  rule.entonces = {
    accion: existing?.entonces?.accion ?? 'deshabilitar',
    preguntas: draft.targets,
  };
  return rule;
}

interface FieldError {
  fieldId: string;
  message: string;
}

function validate(
  draft: Draft,
  idBase: string,
  isNew: boolean,
  existingIds: string[],
): FieldError[] {
  const errors: FieldError[] = [];
  if (isNew) {
    const id = draft.id.trim();
    if (!id) {
      errors.push({ fieldId: `${idBase}-id`, message: 'Escriba el identificador.' });
    } else if (!RULE_ID_PATTERN.test(id)) {
      errors.push({
        fieldId: `${idBase}-id`,
        message:
          'Use minúsculas, números y guiones, hasta 41 caracteres, empezando por letra o número.',
      });
    } else if (existingIds.includes(id)) {
      errors.push({
        fieldId: `${idBase}-id`,
        message: 'Ya existe una regla con ese identificador.',
      });
    }
  }
  draft.conditions.forEach((condition, index) => {
    const prefix = `${idBase}-c${index + 1}`;
    if (condition.columns.length === 0) {
      errors.push({
        fieldId: `${prefix}-columns`,
        message: condition.mode === 'una' ? 'Elija la pregunta.' : 'Elija al menos una pregunta.',
      });
    }
    if (SINGLE_VALUE_OPS.has(condition.op) && !condition.value.trim()) {
      errors.push({ fieldId: `${prefix}-valor`, message: 'Indique el valor.' });
    }
    if (LIST_VALUE_OPS.has(condition.op) && condition.values.length === 0) {
      errors.push({ fieldId: `${prefix}-valores`, message: 'Marque al menos un valor.' });
    }
  });
  if (draft.targets.length === 0) {
    errors.push({
      fieldId: `${idBase}-targets`,
      message: 'Elija al menos una pregunta que no se muestre.',
    });
  }
  return errors;
}

function focusField(fieldId: string) {
  const element = document.getElementById(fieldId);
  const target = element instanceof HTMLFieldSetElement ? element.querySelector('input') : element;
  target?.focus();
}

interface ConditionFieldsProps {
  idBase: string;
  number: number;
  draft: ConditionDraft;
  questions: RuleQuestion[];
  lookup: Map<string, RuleQuestion>;
  errors: Map<string, string>;
  canRemove: boolean;
  onChange: (next: ConditionDraft) => void;
  onRemove: () => void;
}

function FieldMessage({ id, message }: { id: string; message?: string }) {
  return message ? (
    <p id={`${id}-error`} className="mt-1 text-sm text-danger">
      {message}
    </p>
  ) : null;
}

function selectedValues(select: HTMLSelectElement): string[] {
  return Array.from(select.selectedOptions).map((option) => option.value);
}

function ConditionFields({
  idBase,
  number,
  draft,
  questions,
  lookup,
  errors,
  canRemove,
  onChange,
  onRemove,
}: ConditionFieldsProps) {
  const prefix = `${idBase}-c${number}`;
  const first = lookup.get(draft.columns[0] ?? '');
  const kind = kindOf(first);
  const valueOptions = valueOptionsOf(first);
  const operators = OPERATORS_BY_KIND[kind];
  const columnsId = `${prefix}-columns`;
  const hintId = `${prefix}-hint`;
  const multiple = draft.mode !== 'una';
  const compatible = (question: RuleQuestion) =>
    !multiple || !first || signatureOf(question) === signatureOf(first);

  function changeColumns(columns: string[]) {
    const nextKind = kindOf(lookup.get(columns[0] ?? ''));
    const sameQuestion = columns[0] === draft.columns[0];
    onChange({
      ...draft,
      columns,
      op: OPERATORS_BY_KIND[nextKind].includes(draft.op)
        ? draft.op
        : OPERATORS_BY_KIND[nextKind][0],
      value: sameQuestion ? draft.value : '',
      values: sameQuestion ? draft.values : [],
    });
  }

  function changeMode(mode: Mode) {
    onChange({
      ...draft,
      mode,
      columns: mode === 'una' ? draft.columns.slice(0, 1) : draft.columns,
    });
  }

  function describedBy(id: string) {
    return errors.has(id) ? `${id}-error` : undefined;
  }

  let valueControl: ReactNode = null;
  const valorId = `${prefix}-valor`;
  const valoresId = `${prefix}-valores`;
  if (kind === 'NONE' && first) {
    valueControl = (
      <p className="text-sm text-ink-muted sm:self-end">
        Esta pregunta no admite condiciones por valor; use está vacía o tiene respuesta.
      </p>
    );
  } else if (SINGLE_VALUE_OPS.has(draft.op)) {
    valueControl =
      kind === 'TEXT' ? (
        <div>
          <TextField
            id={valorId}
            label={`Valor de la condición ${number}`}
            value={draft.value}
            onChange={(event) => onChange({ ...draft, value: event.target.value })}
            error={errors.get(valorId)}
          />
        </div>
      ) : (
        <div>
          <label htmlFor={valorId} className="block text-sm font-medium">
            {`Valor de la condición ${number}`}
          </label>
          <select
            id={valorId}
            value={draft.value}
            disabled={draft.columns.length === 0}
            onChange={(event) => onChange({ ...draft, value: event.target.value })}
            aria-invalid={errors.has(valorId) ? true : undefined}
            aria-describedby={describedBy(valorId)}
            className={SELECT_CLASS}
          >
            <option value="">
              {draft.columns.length ? 'Elija un valor' : 'Elija primero la pregunta'}
            </option>
            {valueOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <FieldMessage id={valorId} message={errors.get(valorId)} />
        </div>
      );
  } else if (LIST_VALUE_OPS.has(draft.op)) {
    valueControl = (
      <fieldset id={valoresId} aria-describedby={describedBy(valoresId)}>
        <legend className="block text-sm font-medium">{`Valores de la condición ${number}`}</legend>
        <div className="mt-1 flex flex-wrap gap-x-4">
          {valueOptions.map((option) => {
            const optionId = `${valoresId}-${option.value}`;
            return (
              <div key={option.value} className="flex min-h-11 items-center gap-2">
                <input
                  id={optionId}
                  type="checkbox"
                  className="size-5"
                  checked={draft.values.includes(option.value ?? '')}
                  onChange={(event) =>
                    onChange({
                      ...draft,
                      values: event.target.checked
                        ? [...draft.values, option.value ?? '']
                        : draft.values.filter((item) => item !== option.value),
                    })
                  }
                />
                <label htmlFor={optionId}>{option.label}</label>
              </div>
            );
          })}
        </div>
        <FieldMessage id={valoresId} message={errors.get(valoresId)} />
      </fieldset>
    );
  }

  return (
    <li className="rounded-xl border border-ink-muted/30 p-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={`${prefix}-mode`} className="block text-sm font-medium">
            {`Modo de la condición ${number}`}
          </label>
          <select
            id={`${prefix}-mode`}
            value={draft.mode}
            onChange={(event) => changeMode(event.target.value as Mode)}
            className={SELECT_CLASS}
          >
            {(Object.keys(MODE_LABELS) as Mode[]).map((mode) => (
              <option key={mode} value={mode}>
                {MODE_LABELS[mode]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor={columnsId} className="block text-sm font-medium">
            {draft.mode === 'una'
              ? `Pregunta de la condición ${number}`
              : `Preguntas de la condición ${number}`}
          </label>
          {draft.mode === 'una' ? (
            <select
              id={columnsId}
              value={draft.columns[0] ?? ''}
              onChange={(event) => changeColumns(event.target.value ? [event.target.value] : [])}
              aria-invalid={errors.has(columnsId) ? true : undefined}
              aria-describedby={describedBy(columnsId)}
              className={SELECT_CLASS}
            >
              <option value="">Elija una pregunta</option>
              {questions.map((question) => (
                <option key={question.column} value={question.column}>
                  {questionLabel(question)}
                </option>
              ))}
            </select>
          ) : (
            <select
              id={columnsId}
              multiple
              size={5}
              value={draft.columns}
              onChange={(event) => {
                // Se conserva el orden de elección: la primera pregunta fija el tipo.
                const chosen = selectedValues(event.target);
                changeColumns([
                  ...draft.columns.filter((column) => chosen.includes(column)),
                  ...chosen.filter((column) => !draft.columns.includes(column)),
                ]);
              }}
              aria-invalid={errors.has(columnsId) ? true : undefined}
              aria-describedby={[hintId, errors.has(columnsId) ? `${columnsId}-error` : null]
                .filter(Boolean)
                .join(' ')}
              className={SELECT_CLASS}
            >
              {questions.map((question) => (
                <option
                  key={question.column}
                  value={question.column}
                  disabled={!compatible(question)}
                >
                  {questionLabel(question)}
                </option>
              ))}
            </select>
          )}
          {multiple && (
            <p id={hintId} className="mt-1 text-sm text-ink-muted">
              Solo se pueden combinar preguntas del mismo tipo y con las mismas opciones que la
              primera elegida.
            </p>
          )}
          <FieldMessage id={columnsId} message={errors.get(columnsId)} />
        </div>
        <div>
          <label htmlFor={`${prefix}-op`} className="block text-sm font-medium">
            {`Operador de la condición ${number}`}
          </label>
          <select
            id={`${prefix}-op`}
            value={draft.op}
            onChange={(event) =>
              onChange({ ...draft, op: event.target.value, value: '', values: [] })
            }
            className={SELECT_CLASS}
          >
            {operators.map((operator) => (
              <option key={operator} value={operator}>
                {OPERATOR_LABELS[operator]}
              </option>
            ))}
          </select>
        </div>
        {valueControl}
      </div>
      {canRemove && (
        <Button
          variant="secondary"
          className="mt-3"
          aria-label={`Quitar condición ${number}`}
          onClick={onRemove}
        >
          Quitar condición
        </Button>
      )}
    </li>
  );
}

/** Diálogo para crear o editar un salto: condiciones de un nivel y preguntas que se ocultan. */
export function SkipRuleBuilder({
  rule,
  questions,
  existingIds,
  busy = false,
  error = null,
  rejected = null,
  onSave,
  onCancel,
}: SkipRuleBuilderProps) {
  const isNew = !rule;
  const idBase = `salto-${rule?.id ?? 'nuevo'}`;
  const lookup = new Map(questions.map((question) => [question.column ?? '', question]));
  const [draft, setDraft] = useState<Draft>(() => draftOfRule(rule));
  const [errors, setErrors] = useState<Map<string, string>>(new Map());

  if (rule && !isFlatCondition(rule.si)) {
    return (
      <ConfirmDialog
        open
        title={`Regla ${rule.id}`}
        confirmLabel="Cerrar"
        onConfirm={onCancel}
        onCancel={onCancel}
      >
        <p>{describeRule(rule, lookup)}</p>
        <p className="mt-3 font-medium">Esta regla solo se edita importando el archivo.</p>
      </ConfirmDialog>
    );
  }

  function updateCondition(index: number, next: ConditionDraft) {
    setDraft((current) => ({
      ...current,
      conditions: current.conditions.map((item, position) => (position === index ? next : item)),
    }));
  }

  function save() {
    const found = validate(draft, idBase, isNew, existingIds);
    setErrors(new Map(found.map((item) => [item.fieldId, item.message])));
    if (found.length > 0) {
      focusField(found[0].fieldId);
      return;
    }
    onSave(ruleOf(draft, rule));
  }

  const targetsId = `${idBase}-targets`;
  const idFieldId = `${idBase}-id`;
  const preview = describeRule(ruleOf(draft, rule), lookup);

  return (
    <ConfirmDialog
      open
      wide
      title={isNew ? 'Nueva regla de salto' : `Editar regla ${rule.id}`}
      confirmLabel="Guardar"
      cancelLabel="Cancelar"
      busy={busy}
      onConfirm={save}
      onCancel={onCancel}
    >
      {error && (
        <p role="alert" className="mt-1 text-danger">
          {error}
        </p>
      )}
      {rejected}
      <div className="mt-3 grid gap-4 text-ink">
        {isNew && (
          <TextField
            id={idFieldId}
            label="Identificador"
            value={draft.id}
            onChange={(event) => setDraft({ ...draft, id: event.target.value })}
            error={errors.get(idFieldId)}
          />
        )}
        <TextField
          id={`${idBase}-descripcion`}
          label="Descripción"
          value={draft.descripcion}
          onChange={(event) => setDraft({ ...draft, descripcion: event.target.value })}
        />
        <section aria-labelledby={`${idBase}-condiciones`}>
          <h3 id={`${idBase}-condiciones`} className="font-semibold">
            Si se cumple
          </h3>
          <ol className="mt-2 grid gap-3">
            {draft.conditions.map((condition, index) => (
              <ConditionFields
                key={condition.key}
                idBase={idBase}
                number={index + 1}
                draft={condition}
                questions={questions}
                lookup={lookup}
                errors={errors}
                canRemove={draft.conditions.length > 1}
                onChange={(next) => updateCondition(index, next)}
                onRemove={() =>
                  setDraft({
                    ...draft,
                    conditions: draft.conditions.filter((_, position) => position !== index),
                  })
                }
              />
            ))}
          </ol>
          <div className="mt-3 flex flex-wrap items-end gap-4">
            <Button
              variant="secondary"
              onClick={() =>
                setDraft({ ...draft, conditions: [...draft.conditions, emptyCondition()] })
              }
            >
              Agregar condición
            </Button>
            {draft.conditions.length > 1 && (
              <div>
                <label htmlFor={`${idBase}-join`} className="block text-sm font-medium">
                  Unir condiciones con
                </label>
                <select
                  id={`${idBase}-join`}
                  value={draft.join}
                  onChange={(event) =>
                    setDraft({ ...draft, join: event.target.value as 'y' | 'o' })
                  }
                  className={SELECT_CLASS}
                >
                  <option value="y">Y (todas)</option>
                  <option value="o">O (alguna)</option>
                </select>
              </div>
            )}
          </div>
        </section>
        <div>
          <label htmlFor={targetsId} className="block text-sm font-medium">
            Preguntas que no se muestran
          </label>
          <select
            id={targetsId}
            multiple
            size={6}
            value={draft.targets}
            onChange={(event) => setDraft({ ...draft, targets: selectedValues(event.target) })}
            aria-invalid={errors.has(targetsId) ? true : undefined}
            aria-describedby={errors.has(targetsId) ? `${targetsId}-error` : undefined}
            className={SELECT_CLASS}
          >
            {questions.map((question) => (
              <option key={question.column} value={question.column}>
                {questionLabel(question)}
              </option>
            ))}
          </select>
          <FieldMessage id={targetsId} message={errors.get(targetsId)} />
        </div>
        <div>
          <p className="text-sm font-medium">Vista previa</p>
          <p>{preview}</p>
        </div>
      </div>
    </ConfirmDialog>
  );
}
