import { useState } from 'react';
import { Button } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { downloadTextFile } from '../../services/download-file';
import { fetchCreateTableScript, saveTables, type TablesResult } from './admin-api';
import { errorMessage, fieldErrorsOf, handleEditError, type TreeEditing } from './tree-editing';

interface TablesMappingPanelProps {
  /** Tabla de respuestas de cada capítulo (código de capítulo → tabla). */
  tables: Record<string, string>;
  /** Capítulos cuya tabla aún no existe en el esquema. */
  missingChapters: ReadonlySet<string>;
  /** Null en un año publicado: el mapeo se muestra sin edición. */
  editing: TreeEditing | null;
  onSaved: (result: TablesResult) => void;
}

/** Panel capítulo → tabla de respuestas del año, con el script de las tablas faltantes. */
export function TablesMappingPanel({
  tables,
  missingChapters,
  editing,
  onSaved,
}: TablesMappingPanelProps) {
  const chapters = Object.keys(tables).sort();
  const [values, setValues] = useState<Record<string, string>>(tables);
  // Si el árbol se recarga con otro mapeo, los campos lo reflejan (sin perder el aviso).
  const tablesKey = JSON.stringify(tables);
  const [syncedKey, setSyncedKey] = useState(tablesKey);
  if (syncedKey !== tablesKey) {
    setSyncedKey(tablesKey);
    setValues(tables);
  }
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<{ text: string; isError: boolean } | null>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    if (!editing) {
      return;
    }
    setBusy(true);
    setErrors({});
    setMessage(null);
    try {
      const result = await saveTables(editing.survey, editing.year, values, editing.version);
      onSaved(result);
      setMessage({ text: 'Tablas guardadas.', isError: false });
    } catch (caught) {
      const general = handleEditError(caught, editing);
      if (general !== null) {
        const byField = fieldErrorsOf(caught);
        const perChapter = Object.fromEntries(
          chapters
            .filter((chapter) => byField[`tables.${chapter}`])
            .map((chapter) => [chapter, byField[`tables.${chapter}`]]),
        );
        setErrors(perChapter);
        if (Object.keys(perChapter).length === 0) {
          setMessage({ text: general, isError: true });
        }
      }
    } finally {
      setBusy(false);
    }
  }

  async function downloadCreateScript(chapter: string) {
    if (!editing) {
      return;
    }
    setMessage(null);
    try {
      const script = await fetchCreateTableScript(editing.survey, editing.year, chapter);
      downloadTextFile(`tabla-${tables[chapter]}.sql`, script);
    } catch (caught) {
      setMessage({ text: errorMessage(caught), isError: true });
    }
  }

  return (
    <section
      aria-labelledby="titulo-tablas"
      className="mt-6 rounded-xl border border-ink-muted/30 bg-surface p-4"
    >
      <h2 id="titulo-tablas" className="text-xl font-semibold">
        Tablas por capítulo
      </h2>
      <ul className="mt-4 grid gap-4 sm:grid-cols-2">
        {chapters.map((chapter) => {
          const missing = missingChapters.has(chapter);
          return (
            <li key={chapter} className="grid gap-2">
              {editing ? (
                <TextField
                  id={`tabla-${chapter}`}
                  label={`Tabla del capítulo ${chapter}`}
                  value={values[chapter] ?? ''}
                  onChange={(event) =>
                    setValues((current) => ({ ...current, [chapter]: event.target.value }))
                  }
                  error={errors[chapter]}
                />
              ) : (
                <p>
                  <span className="font-medium">Capítulo {chapter}: </span>
                  <span>{tables[chapter]}</span>
                </p>
              )}
              {missing && (
                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded-full border border-danger px-2 py-0.5 text-sm text-danger">
                    Tabla faltante
                  </span>
                  {editing && (
                    <Button
                      variant="secondary"
                      aria-label={`Descargar script CREATE TABLE de ${chapter}`}
                      onClick={() => void downloadCreateScript(chapter)}
                    >
                      Descargar script CREATE TABLE
                    </Button>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>
      {editing && (
        <Button
          className="mt-4"
          onClick={() => void save()}
          disabled={busy}
          aria-busy={busy || undefined}
        >
          Guardar tablas
        </Button>
      )}
      {message && (
        <p
          role={message.isError ? 'alert' : 'status'}
          className={`mt-3 ${message.isError ? 'text-danger' : 'text-success'}`}
        >
          {message.text}
        </p>
      )}
    </section>
  );
}
