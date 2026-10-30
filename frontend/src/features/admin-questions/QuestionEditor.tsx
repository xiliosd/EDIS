import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { downloadTextFile } from '../../services/download-file';
import {
  deactivate,
  editHeader,
  editHelp,
  editText,
  fetchColumnScript,
  type AdminEditResult,
  type AdminEntry,
} from './admin-api';
import {
  entryLabel,
  errorMessage,
  fieldErrorsOf,
  handleEditError,
  INPUT_TYPE_LABELS,
  referencingRuleIds,
  type TreeEditing,
} from './tree-editing';

interface EditableTextProps {
  id: string;
  /** Etiqueta del campo ("Texto de C05"); el botón dice "Editar texto de C05". */
  label: string;
  editLabel: string;
  /** Lo que se muestra fuera de edición (vista previa, con el año ya sustituido). */
  display: ReactNode;
  /** Lo que se edita: el texto guardado, que conserva el comodín 200X. */
  value: string;
  hint?: string;
  editing: TreeEditing;
  save: (text: string) => Promise<AdminEditResult>;
}

/** Texto con edición en línea y guardado explícito (sin autoguardado). */
function EditableText({
  id,
  label,
  editLabel,
  display,
  value,
  hint,
  editing,
  save,
}: EditableTextProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const fieldRef = useRef<HTMLTextAreaElement>(null);
  const editButtonRef = useRef<HTMLButtonElement>(null);
  const returnFocus = useRef(false);

  useEffect(() => {
    if (isEditing) {
      fieldRef.current?.focus();
    } else if (returnFocus.current) {
      returnFocus.current = false;
      editButtonRef.current?.focus();
    }
  }, [isEditing]);

  function open() {
    setDraft(value);
    setError(null);
    setIsEditing(true);
  }

  function close() {
    returnFocus.current = true;
    setIsEditing(false);
  }

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const result = await save(draft);
      editing.onEdited(result);
      close();
    } catch (caught) {
      const message = handleEditError(caught, editing);
      setError(message === null ? null : (fieldErrorsOf(caught).text ?? message));
    } finally {
      setBusy(false);
    }
  }

  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');

  if (!isEditing) {
    return (
      <div className="flex flex-wrap items-start gap-2">
        <div className="min-w-0 flex-1 break-words">{display}</div>
        <Button ref={editButtonRef} variant="secondary" aria-label={editLabel} onClick={open}>
          Editar
        </Button>
      </div>
    );
  }

  return (
    <div className="grid gap-2">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <textarea
        id={id}
        ref={fieldRef}
        value={draft}
        rows={3}
        onChange={(event) => setDraft(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className="block w-full rounded-lg border border-ink-muted/40 bg-surface px-3 py-2 text-base text-ink aria-invalid:border-danger"
      />
      {hint && (
        <p id={hintId} className="text-sm text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-sm text-danger">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => void submit()} disabled={busy} aria-busy={busy || undefined}>
          Guardar
        </Button>
        <Button variant="secondary" onClick={close} disabled={busy}>
          Cancelar
        </Button>
      </div>
    </div>
  );
}

function rulesText(rules: string[]): string {
  if (rules.length === 0) {
    return 'Usada por 0 reglas';
  }
  return `Usada por ${rules.length} ${rules.length === 1 ? 'regla' : 'reglas'}: ${rules.join(', ')}`;
}

interface QuestionEditorProps {
  entry: AdminEntry;
  pageCode: string;
  /** Null en un año publicado: se muestra todo sin controles de edición. */
  editing: TreeEditing | null;
}

/** Una pregunta o enunciado del árbol con sus encabezados, su ayuda y sus acciones. */
export function QuestionEditor({ entry, pageCode, editing }: QuestionEditorProps) {
  const label = entryLabel(entry);
  const capProyId = entry.capProyId ?? 0;
  const idBase = `pregunta-${pageCode}-${capProyId}`;
  const [confirming, setConfirming] = useState(false);
  const [deactivating, setDeactivating] = useState(false);
  const [actionError, setActionError] = useState<{ message: string; rules: string[] } | null>(null);
  const isItem = entry.kind === 'ITEM';
  const pending = entry.columnStatus === 'PENDING_COLUMN';

  async function confirmDeactivate() {
    if (!editing) {
      return;
    }
    setDeactivating(true);
    setActionError(null);
    try {
      const result = await deactivate(editing.survey, editing.year, capProyId, editing.version);
      setConfirming(false);
      editing.onDeactivated(result, capProyId, entry.columnCode || label);
    } catch (caught) {
      setConfirming(false);
      const rules = referencingRuleIds(caught);
      if (rules.length > 0) {
        setActionError({ message: errorMessage(caught), rules });
      } else {
        const message = handleEditError(caught, editing);
        setActionError(message === null ? null : { message, rules: [] });
      }
    } finally {
      setDeactivating(false);
    }
  }

  async function downloadColumnScript() {
    if (!editing) {
      return;
    }
    setActionError(null);
    try {
      const script = await fetchColumnScript(editing.survey, editing.year, capProyId);
      downloadTextFile(`columna-${entry.columnCode ?? capProyId}.sql`, script);
    } catch (caught) {
      setActionError({ message: errorMessage(caught), rules: [] });
    }
  }

  const headers = (entry.headers ?? []).map((header) => {
    const text = header.text ?? '';
    const display =
      header.kind === 'HEADING' ? (
        <p className="text-lg font-semibold">{text}</p>
      ) : (
        <p className="font-medium">{text}</p>
      );
    if (!editing) {
      return <div key={header.codEnc}>{display}</div>;
    }
    return (
      <EditableText
        key={header.codEnc}
        id={`${idBase}-encabezado-${header.codEnc}`}
        label={`Encabezado de ${label}`}
        editLabel={`Editar encabezado de ${label}`}
        display={display}
        value={text}
        editing={editing}
        save={(value) =>
          editHeader(editing.survey, editing.year, header.codEnc ?? 0, value, editing.version)
        }
      />
    );
  });

  const textDisplay = <p className="whitespace-pre-line">{entry.text}</p>;
  const help = entry.help;

  return (
    <article aria-labelledby={`${idBase}-titulo`} className="grid gap-3">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <h3 id={`${idBase}-titulo`} tabIndex={-1} className="text-base font-semibold">
          {isItem ? label : 'Enunciado'}
        </h3>
        {entry.columnCode && <span>Columna {entry.columnCode}</span>}
        {isItem && (
          <span className="rounded-full border border-ink-muted/40 px-2 py-0.5">
            {INPUT_TYPE_LABELS[entry.inputType ?? 'UNKNOWN'] ?? entry.inputType}
          </span>
        )}
        {entry.columnStatus && (
          <span
            className={`rounded-full border px-2 py-0.5 ${
              pending ? 'border-danger text-danger' : 'border-success text-success'
            }`}
          >
            {pending ? 'Pendiente de columna' : 'Activa'}
          </span>
        )}
        {isItem && (
          <span className="text-ink-muted">{rulesText(entry.referencedByRules ?? [])}</span>
        )}
      </div>

      {headers}

      {editing && entry.rawText != null ? (
        <EditableText
          id={`${idBase}-texto`}
          label={`Texto de ${label}`}
          editLabel={`Editar texto de ${label}`}
          display={textDisplay}
          value={entry.rawText}
          hint="Escriba 200X donde debe ir el año; se reemplaza al mostrar la pregunta."
          editing={editing}
          save={(value) =>
            editText(editing.survey, editing.year, capProyId, value, editing.version)
          }
        />
      ) : (
        <>
          {textDisplay}
          {/* Sin el texto guardado no se edita: la vista previa ya trae el año y perdería 200X. */}
          {editing && (
            <p className="text-sm text-ink-muted">
              Este texto no se puede editar porque no llegó el texto guardado. Recargue la página.
            </p>
          )}
        </>
      )}

      {help &&
        (editing ? (
          <EditableText
            id={`${idBase}-ayuda`}
            label={`Ayuda de ${label}`}
            editLabel={`Editar ayuda de ${label}`}
            display={<p className="text-sm text-ink-muted">{help.text}</p>}
            value={help.text ?? ''}
            editing={editing}
            save={(value) =>
              editHelp(editing.survey, editing.year, help.codAyu ?? 0, value, editing.version)
            }
          />
        ) : (
          <p className="text-sm text-ink-muted">{help.text}</p>
        ))}

      {editing && (
        <div className="flex flex-wrap gap-2">
          {pending && (
            <Button
              variant="secondary"
              aria-label={`Descargar script de columna de ${label}`}
              onClick={() => void downloadColumnScript()}
            >
              Descargar script de columna
            </Button>
          )}
          <Button variant="secondary" onClick={() => setConfirming(true)}>
            Desactivar {label}
          </Button>
        </div>
      )}

      {actionError && (
        <div role="alert" className="rounded-xl border border-danger/40 bg-surface p-3">
          <p className="text-danger">{actionError.message}</p>
          {actionError.rules.length > 0 && (
            <ul className="mt-2 list-disc pl-6 text-danger">
              {actionError.rules.map((rule) => (
                <li key={rule}>{rule}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      {editing && confirming && (
        <ConfirmDialog
          open
          title={`Desactivar ${label}`}
          confirmLabel="Desactivar"
          cancelLabel="Cancelar"
          busy={deactivating}
          onCancel={() => setConfirming(false)}
          onConfirm={() => void confirmDeactivate()}
        >
          <p>
            La pregunta deja de aparecer en el cuestionario de {editing.year}. Los demás años no
            cambian.
          </p>
        </ConfirmDialog>
      )}
    </article>
  );
}
