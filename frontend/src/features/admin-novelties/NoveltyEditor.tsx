import { useEffect, useId, useRef, useState } from 'react';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { TextField } from '../../components/TextField';
import { ApiError } from '../../services/api-error';
import { storedLength } from '../../services/latin9-text';
import { errorMessage, fieldErrorsOf } from '../admin-questions/tree-editing';
import { OTHER_CAUSE_MAX_BYTES, type NoveltyCatalogEntry } from './novelties-api';

export interface NoveltyChoice {
  noveltyCode: number;
  otherCause?: string;
}

interface NoveltyEditorProps {
  open: boolean;
  /** Nombre del funcionario, solo para el título del diálogo. */
  personName: string;
  hasNovelty: boolean;
  catalog: NoveltyCatalogEntry[];
  initialCode?: number;
  initialOtherCause?: string;
  busy: boolean;
  /** Rechazo del servidor (422, 404…) que se muestra dentro del diálogo. */
  serverError: unknown;
  onSave: (choice: NoveltyChoice) => void;
  onCancel: () => void;
}

/** Texto y bytes con los que se valida y se envía "Otra causa" (el servidor también recorta). */
export function validateOtherCause(text: string): string | undefined {
  const trimmed = text.trim();
  if (trimmed === '') {
    return 'Escriba la otra causa.';
  }
  const bytes = storedLength(trimmed);
  if (bytes > OTHER_CAUSE_MAX_BYTES) {
    return `La otra causa ocupa ${bytes} bytes y el máximo es ${OTHER_CAUSE_MAX_BYTES}. Acórtela.`;
  }
  return undefined;
}

/**
 * Diálogo para asignar o cambiar la novedad de un funcionario. Se monta con `key` por funcionario,
 * de modo que cada apertura parte de los valores leídos.
 */
export function NoveltyEditor({
  open,
  personName,
  hasNovelty,
  catalog,
  initialCode,
  initialOtherCause,
  busy,
  serverError,
  onSave,
  onCancel,
}: NoveltyEditorProps) {
  const selectId = useId();
  const textId = useId();
  const [code, setCode] = useState<number | undefined>(initialCode);
  const [otherCause, setOtherCause] = useState(initialOtherCause ?? '');
  const [codeError, setCodeError] = useState<string>();
  const [textError, setTextError] = useState<string>();
  const selectRef = useRef<HTMLSelectElement>(null);
  const textRef = useRef<HTMLInputElement>(null);

  const selected = catalog.find((entry) => entry.code === code);
  const needsText = selected?.requiresText === true;
  const bytes = storedLength(otherCause.trim());
  const serverFields = fieldErrorsOf(serverError);

  useEffect(() => {
    if (open) {
      // El diálogo ya enfocó su primer control; esto solo asegura el campo de novedad.
      selectRef.current?.focus();
    }
  }, [open]);

  function save() {
    if (code === undefined || !selected) {
      setCodeError('Seleccione una novedad.');
      selectRef.current?.focus();
      return;
    }
    setCodeError(undefined);
    if (needsText) {
      const problem = validateOtherCause(otherCause);
      setTextError(problem);
      if (problem) {
        textRef.current?.focus();
        return;
      }
    }
    onSave({ noveltyCode: code, otherCause: needsText ? otherCause.trim() : undefined });
  }

  const serverMessage =
    serverError && !(serverError instanceof ApiError && Object.keys(serverFields).length > 0)
      ? errorMessage(serverError)
      : undefined;

  return (
    <ConfirmDialog
      open={open}
      wide
      title={`${hasNovelty ? 'Cambiar' : 'Asignar'} novedad de ${personName}`}
      confirmLabel="Guardar"
      cancelLabel="Cancelar"
      busy={busy}
      onConfirm={save}
      onCancel={onCancel}
    >
      <div className="grid gap-4 text-ink">
        <div>
          <label htmlFor={selectId} className="block text-sm font-medium">
            Novedad
          </label>
          <select
            id={selectId}
            ref={selectRef}
            value={code ?? ''}
            aria-invalid={codeError || serverFields.noveltyCode ? true : undefined}
            aria-describedby={
              codeError || serverFields.noveltyCode ? `${selectId}-error` : undefined
            }
            className="mt-1 block min-h-11 w-full rounded-lg border border-ink-muted/40 bg-surface px-3"
            onChange={(event) => {
              setCode(event.target.value === '' ? undefined : Number(event.target.value));
              setCodeError(undefined);
            }}
          >
            <option value="">Seleccione una novedad</option>
            {catalog.map((entry) => (
              <option key={entry.code} value={entry.code}>
                {entry.name}
              </option>
            ))}
          </select>
          {(codeError || serverFields.noveltyCode) && (
            <p id={`${selectId}-error`} className="mt-1 text-sm text-danger">
              {codeError ?? serverFields.noveltyCode}
            </p>
          )}
        </div>
        {needsText && (
          <TextField
            id={textId}
            ref={textRef}
            label="Otra causa"
            value={otherCause}
            required
            autoComplete="off"
            hint={`${bytes} de ${OTHER_CAUSE_MAX_BYTES} bytes`}
            error={textError ?? serverFields.otherCause}
            onChange={(event) => {
              setOtherCause(event.target.value);
              setTextError(undefined);
            }}
          />
        )}
        {serverMessage && (
          <p role="alert" className="text-sm text-danger">
            {serverMessage}
          </p>
        )}
      </div>
    </ConfirmDialog>
  );
}
