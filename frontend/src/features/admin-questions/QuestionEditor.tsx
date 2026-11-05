import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link } from 'react-router';
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
  DRAFT_ONLY_NOTE,
  handleEditError,
  INPUT_TYPE_LABELS,
  REASON_REQUIRED_NOTE,
  saveBlocked,
  deactivationBlockers,
  type DeactivationBlockers,
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
  save: (text: string, motivo: string | undefined) => Promise<AdminEditResult>;
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
      const result = await save(draft, editing.motivo);
      editing.onEdited(result);
      editing.onSaved?.();
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
        <Button
          onClick={() => void submit()}
          disabled={busy || saveBlocked(editing)}
          aria-busy={busy || undefined}
        >
          Guardar
        </Button>
        <Button variant="secondary" onClick={close} disabled={busy}>
          Cancelar
        </Button>
      </div>
      {saveBlocked(editing) && <p className="text-sm text-ink-muted">{REASON_REQUIRED_NOTE}</p>}
    </div>
  );
}

function rulesText(rules: string[]): string {
  if (rules.length === 0) {
    return 'Usada por 0 reglas';
  }
  return `Usada por ${rules.length} ${rules.length === 1 ? 'regla' : 'reglas'}: ${rules.join(', ')}`;
}

/** Ids separados por coma; cada uno en su propio elemento para poder enlazarlo o leerlo aparte. */
function idList(ids: string[], render: (id: string) => ReactNode) {
  return ids.map((id, index) => (
    <Fragment key={id}>
      {index > 0 && ', '}
      {render(id)}
    </Fragment>
  ));
}

interface BlockersNoticeProps {
  blockers: DeactivationBlockers;
  survey: string;
  year: number;
}

/** Por qué no se pudo desactivar, nombrando solo las listas que traen algo. */
function BlockersNotice({ blockers, survey, year }: BlockersNoticeProps) {
  const { rules, cases } = blockers;
  return (
    <p className="text-danger">
      No se puede desactivar: la usan{' '}
      {rules.length > 0 && (
        <>
          las reglas{' '}
          {idList(rules, (id) => (
            <span>{id}</span>
          ))}
        </>
      )}
      {rules.length > 0 && cases.length > 0 && ' y '}
      {cases.length > 0 && (
        <>
          los casos de prueba{' '}
          {idList(cases, (id) => (
            <Link
              to={`/admin/cuestionario/${survey}/${year}/simulador#caso-${id}`}
              className="text-primary underline"
            >
              {id}
            </Link>
          ))}
        </>
      )}
      .
    </p>
  );
}

interface QuestionEditorProps {
  entry: AdminEntry;
  pageCode: string;
  /** Null en un año publicado: se muestra todo sin controles de edición. */
  editing: TreeEditing | null;
}

type AdminHelp = NonNullable<AdminEntry['help']>;

/**
 * Definición de la ayuda en GRAL_AYUDAS, solo como contexto: se edita en la administración de
 * ayudas (Fase 4), no aquí. Llega en texto plano y React la escapa.
 */
function HelpDefinition({ id, label, help }: { id: string; label: string; help: AdminHelp }) {
  const definition = help.definition;
  return (
    <div
      role="group"
      aria-labelledby={`${id}-titulo`}
      className="rounded border border-ink-muted/40 px-3 py-2 text-sm"
    >
      <p id={`${id}-titulo`} className="font-semibold">
        Definición de la ayuda de {label}
      </p>
      <p className="text-ink-muted">
        {help.codAyu != null
          ? `Definición n.º ${help.codAyu} (solo lectura)`
          : 'Sin definición asociada'}
      </p>
      {definition ? (
        <>
          <p className="font-semibold">{definition.title}</p>
          <p className="whitespace-pre-line">{definition.excerpt}</p>
        </>
      ) : (
        <p>Sin definición en GRAL_AYUDAS</p>
      )}
    </div>
  );
}

/** Una pregunta o enunciado del árbol con sus encabezados, su ayuda y sus acciones. */
export function QuestionEditor({ entry, pageCode, editing }: QuestionEditorProps) {
  const label = entryLabel(entry);
  const capProyId = entry.capProyId ?? 0;
  const idBase = `pregunta-${pageCode}-${capProyId}`;
  const [confirming, setConfirming] = useState(false);
  const [deactivating, setDeactivating] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [blockers, setBlockers] = useState<DeactivationBlockers | null>(null);
  const isItem = entry.kind === 'ITEM';
  const pending = entry.columnStatus === 'PENDING_COLUMN';

  async function confirmDeactivate() {
    if (!editing) {
      return;
    }
    setDeactivating(true);
    setActionError(null);
    setBlockers(null);
    try {
      const result = await deactivate(editing.survey, editing.year, capProyId, editing.version);
      setConfirming(false);
      editing.onDeactivated(result, capProyId, entry.columnCode || label);
    } catch (caught) {
      setConfirming(false);
      const found = deactivationBlockers(caught);
      if (found) {
        setBlockers(found);
      } else {
        setActionError(handleEditError(caught, editing));
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
    setBlockers(null);
    try {
      const script = await fetchColumnScript(editing.survey, editing.year, capProyId);
      downloadTextFile(`columna-${entry.columnCode ?? capProyId}.sql`, script);
    } catch (caught) {
      setActionError(errorMessage(caught));
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
        save={(value, motivo) =>
          editHeader(
            editing.survey,
            editing.year,
            header.codEnc ?? 0,
            value,
            editing.version,
            motivo,
          )
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
          save={(value, motivo) =>
            editText(editing.survey, editing.year, capProyId, value, editing.version, motivo)
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

      {help && (
        <div className="grid gap-1">
          {editing ? (
            <EditableText
              id={`${idBase}-ayuda`}
              label={`Palabra enlazada a la ayuda de ${label}`}
              editLabel={`Editar palabra enlazada de ${label}`}
              display={<p className="text-sm text-ink-muted">{help.text}</p>}
              value={help.text ?? ''}
              hint="Es la palabra o frase de la pregunta que se muestra como enlace a la definición."
              editing={editing}
              save={(value, motivo) =>
                editHelp(
                  editing.survey,
                  editing.year,
                  help.codPreAyu ?? 0,
                  value,
                  editing.version,
                  motivo,
                )
              }
            />
          ) : (
            <p className="text-sm text-ink-muted">
              <span className="font-semibold">Palabra enlazada a la ayuda:</span>{' '}
              <span>{help.text}</span>
            </p>
          )}
          <HelpDefinition id={`${idBase}-definicion`} label={label} help={help} />
        </div>
      )}

      {editing && (
        <div className="flex flex-wrap gap-2">
          {pending && !editing.correction && (
            <Button
              variant="secondary"
              aria-label={`Descargar script de columna de ${label}`}
              onClick={() => void downloadColumnScript()}
            >
              Descargar script de columna
            </Button>
          )}
          <Button
            variant="secondary"
            disabled={editing.correction}
            aria-describedby={editing.correction ? `${idBase}-solo-borrador` : undefined}
            onClick={() => setConfirming(true)}
          >
            Desactivar {label}
          </Button>
          {editing.correction && (
            <span id={`${idBase}-solo-borrador`} className="self-center text-sm text-ink-muted">
              {DRAFT_ONLY_NOTE}
            </span>
          )}
        </div>
      )}

      {(actionError || blockers) && editing && (
        <div role="alert" className="rounded-xl border border-danger/40 bg-surface p-3">
          {blockers ? (
            <BlockersNotice blockers={blockers} survey={editing.survey} year={editing.year} />
          ) : (
            <p className="text-danger">{actionError}</p>
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
