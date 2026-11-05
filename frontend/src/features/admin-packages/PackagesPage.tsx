import { useEffect, useId, useRef, useState, type ChangeEvent } from 'react';
import { Link, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorState } from '../../components/ErrorState';
import { QuestionnaireTabs } from '../../components/QuestionnaireTabs';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { ApiError } from '../../services/api-error';
import { fetchHistory, historyQueryKey } from '../admin-history/history-api';
import { ChecklistView } from '../admin-publish/ChecklistView';
import {
  fetchRuleSet,
  ruleSetQueryKey,
  type PublicationChecklist,
} from '../admin-publish/publish-api';
import { errorMessage } from '../admin-questions/tree-editing';
import { ImportPreviewView } from './ImportPreviewView';
import {
  downloadPackage,
  importPackage,
  MAX_PACKAGE_BYTES,
  type ImportPreview,
  type PackageKind,
} from './packages-api';

const SIZE_ERROR = 'El paquete supera 5 MB.';
const NOT_ZIP_ERROR = 'El paquete debe ser un archivo .zip.';

/** Paquetes de promoción: descargar el del año o el de corrección, e importar uno verificado. */
export function PackagesPage() {
  const { encuesta = '', anio = '' } = useParams();
  const year = Number(anio);
  const title = `Paquetes ${encuesta} ${anio}`;
  useDocumentTitle(title);
  const queryClient = useQueryClient();
  const fileId = useId();
  const historyQuery = useQuery({
    queryKey: historyQueryKey(encuesta, year),
    queryFn: () => fetchHistory(encuesta, year),
  });
  const ruleSetQuery = useQuery({
    queryKey: ruleSetQueryKey(encuesta, year),
    queryFn: () => fetchRuleSet(encuesta, year),
  });

  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [importError, setImportError] = useState<ApiError | string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [blockedChecklist, setBlockedChecklist] = useState<PublicationChecklist | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  // Cada resultado (verificación, error) lleva el foco a su resumen (WCAG 2.4.3).
  const [focusResult, setFocusResult] = useState(0);
  const resultRef = useRef<HTMLElement>(null);
  const importErrorRef = useRef<HTMLDivElement>(null);
  const downloadErrorRef = useRef<HTMLParagraphElement>(null);
  const checklistRef = useRef<HTMLElement>(null);
  const fileErrorRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (focusResult === 0) {
      return;
    }
    (
      resultRef.current ??
      importErrorRef.current ??
      checklistRef.current ??
      downloadErrorRef.current ??
      fileErrorRef.current
    )?.focus();
  }, [focusResult]);

  const published = ruleSetQuery.data?.estado === 'publicado';
  const publishedVersion = ruleSetQuery.data?.publicacion?.version;
  // La corrección solo existe si se guardó algo después de publicar.
  const hasCorrections =
    published &&
    publishedVersion !== undefined &&
    (historyQuery.data?.version ?? 0) > publishedVersion;
  const loading = historyQuery.isPending || ruleSetQuery.isPending;
  const loadFailed = historyQuery.isError || ruleSetQuery.isError;

  const download = useMutation({
    mutationFn: (kind: PackageKind) => downloadPackage(encuesta, year, kind),
    onMutate: () => {
      setDownloadError(null);
      setBlockedChecklist(null);
      setStatusMessage('');
    },
    onSuccess: () => setStatusMessage('El paquete se descargó.'),
    onError: (caught) => {
      if (caught instanceof ApiError && caught.status === 422 && caught.checklist) {
        setBlockedChecklist(caught.checklist);
      } else {
        setDownloadError(errorMessage(caught));
      }
      setFocusResult((count) => count + 1);
    },
  });

  const verify = useMutation({
    mutationFn: (selected: File) => importPackage(encuesta, year, selected, false),
    onMutate: () => {
      setImportError(null);
      setPreview(null);
      setStatusMessage('');
    },
    onSuccess: (result) => {
      setPreview(result);
      setFocusResult((count) => count + 1);
    },
    onError: (caught) => {
      setImportError(caught instanceof ApiError ? caught : errorMessage(caught));
      setFocusResult((count) => count + 1);
    },
  });

  const importing = useMutation({
    mutationFn: (selected: File) => importPackage(encuesta, year, selected, true),
    onMutate: () => setImportError(null),
    onSuccess: () => {
      // Importar reemplaza el archivo de reglas: todo lo del cuestionario se vuelve a leer.
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
      setPreview(null);
      setStatusMessage('El paquete se importó.');
    },
    onError: (caught) => {
      setImportError(caught instanceof ApiError ? caught : errorMessage(caught));
      setFocusResult((count) => count + 1);
    },
  });

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const chosen = event.target.files?.[0] ?? null;
    setPreview(null);
    setImportError(null);
    setStatusMessage('');
    // Cambiar de archivo invalida la verificación anterior: se debe verificar de nuevo.
    if (chosen && chosen.size > MAX_PACKAGE_BYTES) {
      setFile(null);
      setFileError(SIZE_ERROR);
    } else if (chosen && !chosen.name.toLowerCase().endsWith('.zip')) {
      setFile(null);
      setFileError(NOT_ZIP_ERROR);
    } else {
      setFile(chosen);
      setFileError(null);
    }
  }

  function confirmImport() {
    setConfirming(false);
    if (file) {
      importing.mutate(file);
    }
  }

  const busy = download.isPending || verify.isPending || importing.isPending;
  const canImport = preview !== null && !preview.alreadyImported && file !== null;

  return (
    <section aria-labelledby="titulo-paquetes">
      <Link
        to="/admin/cuestionario"
        className="inline-flex min-h-11 items-center text-primary underline"
      >
        Volver a los años
      </Link>
      <h1 id="titulo-paquetes" className="text-2xl font-semibold">
        {title}
      </h1>
      <QuestionnaireTabs survey={encuesta} year={year} />

      {loading && !loadFailed && (
        <p role="status" aria-live="polite" className="mt-6 text-ink-muted">
          Cargando el estado del año…
        </p>
      )}
      {loadFailed && (
        <ErrorState
          message={errorMessage(historyQuery.error ?? ruleSetQuery.error)}
          onRetry={() => {
            void historyQuery.refetch();
            void ruleSetQuery.refetch();
          }}
        />
      )}

      <p role="status" aria-live="polite" className="mt-4">
        {statusMessage}
      </p>

      <section aria-labelledby="titulo-descargar" className="mt-6">
        <h2 id="titulo-descargar" className="text-xl font-semibold">
          Descargar
        </h2>
        <p className="mt-2 text-ink-muted">
          El paquete lleva manifiesto.json, reglas.json, frm.sql y columnas.sql. Solo se genera de
          un año publicado.
        </p>
        <div className="mt-3 flex flex-wrap gap-3">
          <Button disabled={!published || busy} onClick={() => download.mutate('anio')}>
            Descargar paquete del año
          </Button>
          <Button
            variant="secondary"
            disabled={!hasCorrections || busy}
            onClick={() => download.mutate('correccion')}
          >
            Descargar paquete de corrección
          </Button>
        </div>
        {!loading && !loadFailed && !published && (
          <p className="mt-2 text-ink-muted">Publique el año para generar sus paquetes.</p>
        )}
        {published && !hasCorrections && (
          <p className="mt-2 text-ink-muted">No hay correcciones posteriores a la publicación.</p>
        )}
        {downloadError && (
          <p
            ref={downloadErrorRef}
            role="alert"
            tabIndex={-1}
            className="mt-4 rounded-xl border border-danger/40 bg-surface p-4 text-danger"
          >
            {downloadError}
          </p>
        )}
        {blockedChecklist && (
          <ChecklistView
            ref={checklistRef}
            checklist={blockedChecklist}
            survey={encuesta}
            year={year}
            warnings={[]}
          />
        )}
      </section>

      <section aria-labelledby="titulo-importar" className="mt-10">
        <h2 id="titulo-importar" className="text-xl font-semibold">
          Importar
        </h2>
        <p className="mt-2">
          Ejecute primero columnas.sql y frm.sql con su usuario de base de datos.
        </p>
        <div className="mt-3 grid gap-1">
          <label htmlFor={fileId} className="font-medium">
            Paquete de promoción (.zip, máximo 5 MB)
          </label>
          <input
            id={fileId}
            type="file"
            accept=".zip,application/zip"
            onChange={chooseFile}
            aria-describedby={fileError ? `${fileId}-error` : undefined}
            className="min-h-11"
          />
        </div>
        {fileError && (
          <p id={`${fileId}-error`} ref={fileErrorRef} role="alert" className="mt-2 text-danger">
            {fileError}
          </p>
        )}
        <div className="mt-3 flex flex-wrap gap-3">
          <Button
            variant="secondary"
            disabled={!file || busy}
            aria-busy={verify.isPending || undefined}
            onClick={() => file && verify.mutate(file)}
          >
            Verificar
          </Button>
          <Button
            disabled={!canImport || busy}
            aria-busy={importing.isPending || undefined}
            onClick={() => setConfirming(true)}
          >
            Importar
          </Button>
        </div>

        {importError && (
          <div
            ref={importErrorRef}
            role="alert"
            tabIndex={-1}
            className="mt-4 rounded-xl border border-danger/40 bg-surface p-4"
          >
            <p className="text-danger">
              {typeof importError === 'string' ? importError : importError.detail}
            </p>
            {typeof importError !== 'string' && (importError.differences ?? []).length > 0 && (
              <ul aria-label="Diferencias encontradas" className="mt-2 list-disc pl-6">
                {(importError.differences ?? []).map((difference, index) => (
                  <li key={`${difference}-${index}`}>{difference}</li>
                ))}
              </ul>
            )}
          </div>
        )}
        {preview && <ImportPreviewView ref={resultRef} preview={preview} />}
        {preview?.alreadyImported && (
          <p className="mt-2 text-ink-muted">No hace falta importarlo de nuevo.</p>
        )}
      </section>

      {confirming && (
        <ConfirmDialog
          open
          title="Importar el paquete"
          confirmLabel="Confirmar importación"
          cancelLabel="Cancelar"
          onCancel={() => setConfirming(false)}
          onConfirm={confirmImport}
        >
          <p>
            Importar reemplaza el archivo de reglas de {encuesta} {year} por el del paquete. No
            modifica las tablas FRM_*. ¿Continuar?
          </p>
        </ConfirmDialog>
      )}
    </section>
  );
}
