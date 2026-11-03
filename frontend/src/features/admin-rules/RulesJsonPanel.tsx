import { useState, type ChangeEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorState } from '../../components/ErrorState';
import { ApiError } from '../../services/api-error';
import { downloadRules, fetchRulesExport, rulesExportQueryKey, type RuleSet } from './rules-api';

interface RulesJsonPanelProps {
  survey: string;
  year: number;
  /** El año está publicado: se puede ver y descargar, no importar. */
  readOnly: boolean;
  /** Importa el archivo; devuelve el mensaje de error si falló de forma que se muestra aquí. */
  onImport: (rules: RuleSet) => Promise<string | null>;
}

const MAX_IMPORT_BYTES = 1024 * 1024;
const GENERIC_ERROR = 'No fue posible completar la solicitud.';

// jsdom no implementa Blob.text(); FileReader funciona en todos los navegadores.
function readFileText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(file);
  });
}

interface PendingImport {
  fileName: string;
  rules: RuleSet;
}

/** Vista de solo lectura del archivo de reglas, con descarga e importación. */
export function RulesJsonPanel({ survey, year, readOnly, onImport }: RulesJsonPanelProps) {
  const exportQuery = useQuery({
    queryKey: rulesExportQueryKey(survey, year),
    queryFn: () => fetchRulesExport(survey, year),
  });
  const [message, setMessage] = useState<string | null>(null);
  const [pending, setPending] = useState<PendingImport | null>(null);
  const [busy, setBusy] = useState(false);

  async function download() {
    setMessage(null);
    try {
      await downloadRules(survey, year);
    } catch (caught) {
      setMessage(caught instanceof ApiError ? caught.detail : GENERIC_ERROR);
    }
  }

  async function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target;
    const file = input.files?.[0];
    // Permite elegir de nuevo el mismo archivo después de corregirlo.
    input.value = '';
    setMessage(null);
    if (!file) {
      return;
    }
    if (file.size > MAX_IMPORT_BYTES) {
      setMessage('El archivo supera 1 MB.');
      return;
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(await readFileText(file));
    } catch {
      setMessage(`El archivo «${file.name}» no es un JSON válido.`);
      return;
    }
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      setMessage(`El archivo «${file.name}» debe contener un objeto JSON con las reglas.`);
      return;
    }
    setPending({ fileName: file.name, rules: parsed as RuleSet });
  }

  async function confirmImport() {
    if (!pending) {
      return;
    }
    setBusy(true);
    try {
      const error = await onImport(pending.rules);
      setMessage(error);
      setPending(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="titulo-json" className="mt-10">
      <h2 id="titulo-json" className="text-xl font-semibold">
        Archivo JSON de reglas
      </h2>
      {exportQuery.isPending && (
        <p role="status" aria-live="polite" className="mt-3 text-ink-muted">
          Cargando el archivo…
        </p>
      )}
      {exportQuery.isError && (
        <ErrorState
          message="No fue posible leer el archivo de reglas."
          onRetry={() => void exportQuery.refetch()}
        />
      )}
      {exportQuery.data !== undefined && (
        // Se puede desplazar: necesita foco para leerse con teclado (WCAG 2.1.1).
        <div
          role="region"
          aria-label="Archivo de reglas en JSON"
          // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
          tabIndex={0}
          className="mt-3 max-h-96 overflow-auto rounded-xl border border-ink-muted/30 bg-surface p-3"
        >
          <pre className="text-sm">{exportQuery.data}</pre>
        </div>
      )}
      <div className="mt-4 flex flex-wrap items-end gap-4">
        <Button variant="secondary" onClick={() => void download()}>
          Descargar JSON
        </Button>
        <div>
          <label htmlFor="importar-json" className="block text-sm font-medium">
            Importar JSON
          </label>
          <input
            id="importar-json"
            type="file"
            accept=".json,application/json"
            disabled={readOnly}
            onChange={(event) => void chooseFile(event)}
            className="mt-1 block min-h-11 text-base"
          />
          <p className="text-sm text-ink-muted">Hasta 1 MB. Reemplaza las reglas del año.</p>
        </div>
      </div>
      {message && (
        <p role="alert" className="mt-3 text-danger">
          {message}
        </p>
      )}
      {pending && (
        <ConfirmDialog
          open
          title="Importar reglas"
          confirmLabel="Importar"
          cancelLabel="Cancelar"
          busy={busy}
          onCancel={() => setPending(null)}
          onConfirm={() => void confirmImport()}
        >
          <p>
            Las reglas, validaciones y casos de {year} se reemplazarán por el contenido de «
            {pending.fileName}». ¿Desea continuar?
          </p>
        </ConfirmDialog>
      )}
    </section>
  );
}
