import { useState, type ReactNode } from 'react';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { TextField } from '../../components/TextField';
import { downloadTextFile } from '../../services/download-file';
import {
  addQuestion,
  fetchColumnScript,
  type AdminPage,
  type NewQuestionRequest,
} from './admin-api';
import {
  entryLabel,
  errorMessage,
  fieldErrorsOf,
  handleEditError,
  INPUT_TYPE_LABELS,
  type TreeEditing,
} from './tree-editing';

/** Grupo de opciones existente en el árbol, con una pregunta que lo usa como referencia. */
export interface OptionGroupChoice {
  group: number;
  example: string;
}

interface AddQuestionDialogProps {
  page: AdminPage;
  optionGroups: OptionGroupChoice[];
  editing: TreeEditing;
}

type InputType = NewQuestionRequest['inputType'];

interface FormValues {
  numeral: string;
  columnCode: string;
  text: string;
  inputType: InputType;
  optionGroup: string;
  afterCapProyId: string;
  columnType: string;
  columnLength: string;
}

type FieldName = keyof FormValues;

// Orden de los campos en pantalla: el foco va al primero con error.
const FIELD_ORDER: FieldName[] = [
  'numeral',
  'columnCode',
  'text',
  'inputType',
  'optionGroup',
  'afterCapProyId',
  'columnType',
  'columnLength',
];

const INPUT_TYPES: InputType[] = ['SINGLE_CHOICE', 'CHECKBOX', 'TEXT'];

interface AddedQuestion {
  capProyId: number;
  label: string;
  columnCode: string;
  pending: boolean;
}

function validate(values: FormValues): Partial<Record<FieldName, string>> {
  const errors: Partial<Record<FieldName, string>> = {};
  if (!values.numeral.trim()) {
    errors.numeral = 'Escriba el numeral.';
  }
  if (!values.columnCode.trim()) {
    errors.columnCode = 'Escriba el código de la columna.';
  }
  if (!values.text.trim()) {
    errors.text = 'Escriba el texto.';
  }
  if (values.inputType === 'SINGLE_CHOICE' && !values.optionGroup) {
    errors.optionGroup = 'Elija el grupo de opciones.';
  }
  if (values.columnType && !/^\d+$/.test(values.columnLength.trim())) {
    errors.columnLength = 'Indique la longitud de la columna.';
  }
  return errors;
}

function toRequest(values: FormValues, version: number): NewQuestionRequest {
  const request: NewQuestionRequest = {
    numeral: values.numeral.trim(),
    columnCode: values.columnCode.trim(),
    text: values.text,
    inputType: values.inputType,
    version,
  };
  if (values.inputType !== 'TEXT' && values.optionGroup) {
    request.optionGroup = Number(values.optionGroup);
  }
  if (values.afterCapProyId) {
    request.afterCapProyId = Number(values.afterCapProyId);
  }
  if (values.columnType) {
    request.columnType = values.columnType;
    request.columnLength = Number(values.columnLength.trim());
  }
  return request;
}

interface SelectFieldProps {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
  children: ReactNode;
}

function SelectField({ id, label, value, onChange, error, disabled, children }: SelectFieldProps) {
  const errorId = `${id}-error`;
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <select
        id={id}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className="mt-1 block min-h-11 w-full rounded-lg border border-ink-muted/40 bg-surface px-3 text-base text-ink aria-invalid:border-danger disabled:opacity-60"
      >
        {children}
      </select>
      {error && (
        <p id={errorId} className="mt-1 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

/** Botón y diálogo para agregar una pregunta propia del año a una página. */
export function AddQuestionDialog({ page, optionGroups, editing }: AddQuestionDialogProps) {
  const pageCode = page.pageCode ?? '';
  const entries = page.entries ?? [];
  const idBase = `nueva-${pageCode}`;
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [values, setValues] = useState<FormValues>(() => emptyValues());
  const [errors, setErrors] = useState<Partial<Record<FieldName, string>>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [added, setAdded] = useState<AddedQuestion | null>(null);
  const [scriptError, setScriptError] = useState<string | null>(null);

  function emptyValues(): FormValues {
    const last = entries[entries.length - 1];
    return {
      numeral: '',
      columnCode: '',
      text: '',
      inputType: 'SINGLE_CHOICE',
      optionGroup: '',
      afterCapProyId: last?.capProyId ? String(last.capProyId) : '',
      columnType: '',
      columnLength: '',
    };
  }

  function update(field: FieldName, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  function openDialog() {
    setValues(emptyValues());
    setErrors({});
    setGeneralError(null);
    setOpen(true);
  }

  function showErrors(found: Partial<Record<FieldName, string>>) {
    setErrors(found);
    const first = FIELD_ORDER.find((field) => found[field]);
    if (first) {
      document.getElementById(`${idBase}-${first}`)?.focus();
    }
  }

  async function submit() {
    const found = validate(values);
    if (Object.keys(found).length > 0) {
      showErrors(found);
      return;
    }
    setBusy(true);
    setErrors({});
    setGeneralError(null);
    const before = new Set(entries.map((item) => item.capProyId));
    try {
      const result = await addQuestion(
        editing.survey,
        editing.year,
        pageCode,
        toRequest(values, editing.version),
      );
      editing.onEdited(result);
      const created = (result.page?.entries ?? []).find((item) => !before.has(item.capProyId));
      setAdded(
        created
          ? {
              capProyId: created.capProyId ?? 0,
              label: entryLabel(created),
              columnCode: created.columnCode ?? values.columnCode.trim(),
              pending: created.columnStatus === 'PENDING_COLUMN',
            }
          : null,
      );
      setScriptError(null);
      setOpen(false);
    } catch (caught) {
      const message = handleEditError(caught, editing);
      if (message === null) {
        setOpen(false);
        return;
      }
      const serverErrors = fieldErrorsOf(caught);
      const known = Object.fromEntries(
        FIELD_ORDER.filter((field) => serverErrors[field]).map((field) => [
          field,
          serverErrors[field],
        ]),
      ) as Partial<Record<FieldName, string>>;
      if (Object.keys(known).length > 0) {
        showErrors(known);
      } else {
        setGeneralError(message);
      }
    } finally {
      setBusy(false);
    }
  }

  async function downloadScript(question: AddedQuestion) {
    setScriptError(null);
    try {
      const script = await fetchColumnScript(editing.survey, editing.year, question.capProyId);
      downloadTextFile(`columna-${question.columnCode}.sql`, script);
    } catch (caught) {
      setScriptError(errorMessage(caught));
    }
  }

  const fieldId = (field: FieldName) => `${idBase}-${field}`;
  const isText = values.inputType === 'TEXT';

  return (
    <div className="mt-4">
      <Button variant="secondary" onClick={openDialog}>
        Agregar pregunta en {pageCode}
      </Button>

      {added && (
        <div role="status" className="mt-3 rounded-xl border border-ink-muted/30 bg-surface p-3">
          <p>
            {added.pending
              ? `Se agregó ${added.label}. Su columna está pendiente: descargue el script y entréguelo a la persona técnica.`
              : `Se agregó ${added.label} con su columna activa.`}
          </p>
          {added.pending && (
            <Button variant="secondary" className="mt-2" onClick={() => void downloadScript(added)}>
              Descargar script de columna
            </Button>
          )}
          {scriptError && <p className="mt-2 text-danger">{scriptError}</p>}
        </div>
      )}

      {open && (
        <ConfirmDialog
          open
          title={`Agregar pregunta en ${pageCode}`}
          confirmLabel="Agregar"
          cancelLabel="Cancelar"
          busy={busy}
          onCancel={() => setOpen(false)}
          onConfirm={() => void submit()}
        >
          <p>La pregunta queda solo en {editing.year}. Si su columna no existe, queda pendiente.</p>
          {generalError && (
            <p role="alert" className="mt-3 text-danger">
              {generalError}
            </p>
          )}
          <div className="mt-4 grid gap-4 text-ink">
            <TextField
              id={fieldId('numeral')}
              label="Numeral"
              value={values.numeral}
              onChange={(event) => update('numeral', event.target.value)}
              error={errors.numeral}
            />
            <TextField
              id={fieldId('columnCode')}
              label="Código de columna"
              value={values.columnCode}
              onChange={(event) => update('columnCode', event.target.value)}
              error={errors.columnCode}
            />
            <div>
              <label htmlFor={fieldId('text')} className="block text-sm font-medium">
                Texto
              </label>
              <textarea
                id={fieldId('text')}
                rows={3}
                value={values.text}
                onChange={(event) => update('text', event.target.value)}
                aria-invalid={errors.text ? true : undefined}
                aria-describedby={errors.text ? `${fieldId('text')}-error` : undefined}
                className="mt-1 block w-full rounded-lg border border-ink-muted/40 bg-surface px-3 py-2 text-base text-ink aria-invalid:border-danger"
              />
              {errors.text && (
                <p id={`${fieldId('text')}-error`} className="mt-1 text-sm text-danger">
                  {errors.text}
                </p>
              )}
            </div>
            <SelectField
              id={fieldId('inputType')}
              label="Tipo de respuesta"
              value={values.inputType}
              onChange={(value) => update('inputType', value)}
              error={errors.inputType}
            >
              {INPUT_TYPES.map((type) => (
                <option key={type} value={type}>
                  {INPUT_TYPE_LABELS[type]}
                </option>
              ))}
            </SelectField>
            <SelectField
              id={fieldId('optionGroup')}
              label="Grupo de opciones"
              value={isText ? '' : values.optionGroup}
              onChange={(value) => update('optionGroup', value)}
              error={isText ? undefined : errors.optionGroup}
              disabled={isText}
            >
              <option value="">Sin grupo</option>
              {optionGroups.map((choice) => (
                <option key={choice.group} value={String(choice.group)}>
                  {`Grupo ${choice.group} (como ${choice.example})`}
                </option>
              ))}
            </SelectField>
            <SelectField
              id={fieldId('afterCapProyId')}
              label="Ubicación"
              value={values.afterCapProyId}
              onChange={(value) => update('afterCapProyId', value)}
              error={errors.afterCapProyId}
            >
              <option value="">Al inicio de la página</option>
              {entries.map((item) => (
                <option key={item.capProyId} value={String(item.capProyId)}>
                  {`Después de ${entryLabel(item)}`}
                </option>
              ))}
            </SelectField>
            <SelectField
              id={fieldId('columnType')}
              label="Tipo de columna"
              value={values.columnType}
              onChange={(value) => update('columnType', value)}
              error={errors.columnType}
            >
              <option value="">Deducir de la pregunta</option>
              <option value="VARCHAR2">VARCHAR2 (texto)</option>
              <option value="NUMBER">NUMBER (número)</option>
            </SelectField>
            {values.columnType && (
              <TextField
                id={fieldId('columnLength')}
                label="Longitud de columna"
                inputMode="numeric"
                value={values.columnLength}
                onChange={(event) => update('columnLength', event.target.value)}
                error={errors.columnLength}
              />
            )}
          </div>
        </ConfirmDialog>
      )}
    </div>
  );
}
