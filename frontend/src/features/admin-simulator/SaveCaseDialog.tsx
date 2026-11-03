import { useState } from 'react';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { TextField } from '../../components/TextField';

/** Mismos límites que valida el servidor para un caso. */
const CASE_ID_PATTERN = /^[a-z0-9][a-z0-9-]{0,40}$/;
const MAX_NAME_LENGTH = 100;

interface SaveCaseDialogProps {
  /** Ids de los casos del año: guardar con uno de ellos lo reemplaza. */
  existingIds: string[];
  busy?: boolean;
  /** Error del servidor al guardar; el diálogo sigue abierto con lo escrito. */
  error?: string | null;
  onSave: (draft: { id: string; name: string }) => void;
  onCancel: () => void;
}

/** Pide el identificador y el nombre del caso que se va a guardar desde el simulador. */
export function SaveCaseDialog({
  existingIds,
  busy = false,
  error = null,
  onSave,
  onCancel,
}: SaveCaseDialogProps) {
  const [id, setId] = useState('');
  const [name, setName] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const trimmedId = id.trim();
  const trimmedName = name.trim();
  const idError = CASE_ID_PATTERN.test(trimmedId)
    ? undefined
    : 'Use minúsculas, números y guiones, empezando con letra o número (hasta 41 caracteres).';
  const nameError =
    trimmedName === ''
      ? 'Escriba el nombre del caso.'
      : trimmedName.length > MAX_NAME_LENGTH
        ? `El nombre admite hasta ${MAX_NAME_LENGTH} caracteres.`
        : undefined;

  function submit() {
    setSubmitted(true);
    if (!idError && !nameError) {
      onSave({ id: trimmedId, name: trimmedName });
    }
  }

  return (
    <ConfirmDialog
      open
      title="Guardar como caso de prueba"
      confirmLabel="Guardar caso"
      cancelLabel="Cancelar"
      busy={busy}
      onConfirm={submit}
      onCancel={onCancel}
    >
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
        className="grid gap-4"
      >
        <p>Se guardan las respuestas actuales y lo que el simulador muestra en esta página.</p>
        <TextField
          id="caso-nuevo-id"
          label="Identificador del caso"
          value={id}
          onChange={(event) => setId(event.target.value)}
          error={submitted ? idError : undefined}
          autoComplete="off"
          spellCheck={false}
        />
        {existingIds.includes(trimmedId) && (
          <p className="text-sm">Ya existe un caso con este id: se reemplazará.</p>
        )}
        <TextField
          id="caso-nuevo-nombre"
          label="Nombre del caso"
          value={name}
          onChange={(event) => setName(event.target.value)}
          error={submitted ? nameError : undefined}
          autoComplete="off"
        />
        {error && (
          <p role="alert" className="text-danger">
            {error}
          </p>
        )}
      </form>
    </ConfirmDialog>
  );
}
