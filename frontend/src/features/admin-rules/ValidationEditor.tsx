import { useRef, useState } from 'react';
import { Button } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { FindingNotes, findingsOfElements } from './AnalysisSummary';
import type { AnalysisReport, FieldValidation, RuleQuestion } from './rules-api';

interface ValidationEditorProps {
  /** Preguntas de la página elegida. */
  questions: RuleQuestion[];
  validations: Record<string, FieldValidation>;
  report?: AnalysisReport;
  disabled?: boolean;
  onSave: (column: string, validation: FieldValidation) => Promise<void>;
  onDelete: (column: string) => Promise<void>;
}

const VALIDATION_TYPES = [
  { value: 'entero', label: 'Entero' },
  { value: 'decimal', label: 'Decimal' },
  { value: 'texto', label: 'Texto' },
];

interface FormValues {
  obligatoria: boolean;
  tipo: string;
  min: string;
  max: string;
  longitudMax: string;
}

type NumericField = 'min' | 'max' | 'longitudMax';

function valuesOf(validation: FieldValidation | undefined): FormValues {
  return {
    obligatoria: validation?.obligatoria ?? false,
    tipo: validation?.tipo ?? '',
    min: validation?.min != null ? String(validation.min) : '',
    max: validation?.max != null ? String(validation.max) : '',
    longitudMax: validation?.longitudMax != null ? String(validation.longitudMax) : '',
  };
}

/** Un campo vacío omite la clave; solo las preguntas de texto llevan tipo, rango y longitud. */
function toValidation(values: FormValues, isText: boolean): FieldValidation {
  const validation: FieldValidation = {};
  if (values.obligatoria) {
    validation.obligatoria = true;
  }
  if (isText) {
    if (values.tipo) {
      validation.tipo = values.tipo;
    }
    if (values.min.trim()) {
      validation.min = Number(values.min);
    }
    if (values.max.trim()) {
      validation.max = Number(values.max);
    }
    if (values.longitudMax.trim()) {
      validation.longitudMax = Number(values.longitudMax);
    }
  }
  return validation;
}

function isNumber(text: string): boolean {
  return text.trim() === '' || Number.isFinite(Number(text));
}

interface ValidationRowProps {
  question: RuleQuestion;
  validation: FieldValidation | undefined;
  report?: AnalysisReport;
  disabled: boolean;
  onSave: ValidationEditorProps['onSave'];
  onDelete: ValidationEditorProps['onDelete'];
}

function ValidationRow({
  question,
  validation,
  report,
  disabled,
  onSave,
  onDelete,
}: ValidationRowProps) {
  const column = question.column ?? '';
  const isText = question.inputType === 'TEXT';
  const [values, setValues] = useState<FormValues>(() => valuesOf(validation));
  const [errors, setErrors] = useState<Partial<Record<NumericField, string>>>({});
  // Mientras se guarda no se deshabilitan los botones: perderían el foco (WCAG 2.4.3).
  const busy = useRef(false);
  // Al llegar la validación guardada, el formulario se reinicia con sus valores.
  const serialized = JSON.stringify(validation ?? null);
  const [seen, setSeen] = useState(serialized);
  if (seen !== serialized) {
    setSeen(serialized);
    setValues(valuesOf(validation));
    setErrors({});
  }
  const fieldId = (name: string) => `validacion-${column}-${name}`;

  function update<K extends keyof FormValues>(field: K, value: FormValues[K]) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  async function save() {
    const found: Partial<Record<NumericField, string>> = {};
    for (const field of ['min', 'max', 'longitudMax'] as const) {
      if (isText && !isNumber(values[field])) {
        found[field] = 'Escriba un número.';
      }
    }
    setErrors(found);
    const first = (['min', 'max', 'longitudMax'] as const).find((field) => found[field]);
    if (first) {
      document.getElementById(fieldId(first))?.focus();
      return;
    }
    const built = toValidation(values, isText);
    if (busy.current) {
      return;
    }
    busy.current = true;
    try {
      // Una validación sin ninguna regla no se guarda vacía: se quita la que existía.
      if (Object.keys(built).length === 0) {
        if (validation) {
          await onDelete(column);
        }
      } else {
        await onSave(column, built);
      }
    } finally {
      busy.current = false;
    }
  }

  async function remove() {
    if (busy.current) {
      return;
    }
    busy.current = true;
    try {
      await onDelete(column);
    } finally {
      busy.current = false;
    }
  }

  const findings = findingsOfElements(report, [`validacion:${column}`, `pregunta:${column}`]);

  return (
    <li
      id={`pregunta-${column}`}
      tabIndex={-1}
      className="rounded-xl border border-ink-muted/30 p-4"
    >
      <fieldset aria-label={`Validación de ${column}`} disabled={disabled}>
        <legend className="sr-only">{`Validación de ${column}`}</legend>
        <p className="font-medium">
          {column} <span className="font-normal text-ink-muted">{question.text}</span>
        </p>
        <div className="mt-3 flex min-h-11 items-center gap-2">
          <input
            id={fieldId('obligatoria')}
            type="checkbox"
            checked={values.obligatoria}
            onChange={(event) => update('obligatoria', event.target.checked)}
            className="size-5"
          />
          <label htmlFor={fieldId('obligatoria')}>Obligatoria</label>
        </div>
        {isText && (
          <div className="mt-2 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <label htmlFor={fieldId('tipo')} className="block text-sm font-medium">
                Tipo
              </label>
              <select
                id={fieldId('tipo')}
                value={values.tipo}
                onChange={(event) => update('tipo', event.target.value)}
                className="mt-1 block min-h-11 w-full rounded-lg border border-ink-muted/40 bg-surface px-3 text-base text-ink"
              >
                <option value="">Sin tipo</option>
                {VALIDATION_TYPES.map((type) => (
                  <option key={type.value} value={type.value}>
                    {type.label}
                  </option>
                ))}
              </select>
            </div>
            <TextField
              id={fieldId('min')}
              label="Mínimo"
              inputMode="decimal"
              value={values.min}
              onChange={(event) => update('min', event.target.value)}
              error={errors.min}
            />
            <TextField
              id={fieldId('max')}
              label="Máximo"
              inputMode="decimal"
              value={values.max}
              onChange={(event) => update('max', event.target.value)}
              error={errors.max}
            />
            <TextField
              id={fieldId('longitudMax')}
              label="Longitud máxima"
              inputMode="numeric"
              value={values.longitudMax}
              onChange={(event) => update('longitudMax', event.target.value)}
              error={errors.longitudMax}
            />
          </div>
        )}
        <div className="mt-3 flex flex-wrap gap-3">
          <Button
            variant="secondary"
            aria-label={`Guardar validación de ${column}`}
            disabled={disabled}
            onClick={() => void save()}
          >
            Guardar
          </Button>
          {validation && (
            <Button
              variant="secondary"
              aria-label={`Quitar validación de ${column}`}
              disabled={disabled}
              onClick={() => void remove()}
            >
              Quitar
            </Button>
          )}
        </div>
        <FindingNotes findings={findings} />
      </fieldset>
    </li>
  );
}

/** Validaciones de las preguntas de una página: obligatoria y, en texto, tipo, rango y longitud. */
export function ValidationEditor({
  questions,
  validations,
  report,
  disabled = false,
  onSave,
  onDelete,
}: ValidationEditorProps) {
  if (questions.length === 0) {
    return <p className="mt-3">Esta página no tiene preguntas.</p>;
  }
  return (
    <ul className="mt-3 grid gap-3">
      {questions.map((question) => {
        const column = question.column ?? '';
        const validation = validations[column];
        return (
          <ValidationRow
            key={column}
            question={question}
            validation={validation}
            report={report}
            disabled={disabled}
            onSave={onSave}
            onDelete={onDelete}
          />
        );
      })}
    </ul>
  );
}
