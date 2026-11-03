import { useCallback, useEffect, useId, useRef, type ReactNode } from 'react';
import { Button } from './Button';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  /** Sin etiqueta no hay botón de cancelar (diálogos informativos con un solo botón). */
  cancelLabel?: string;
  /** Para formularios largos: más ancho y con desplazamiento propio. */
  wide?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  busy?: boolean;
}

/** Diálogo modal nativo: atrapa el foco y cierra con Esc sin dependencias. */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  cancelLabel,
  wide = false,
  onConfirm,
  onCancel,
  busy = false,
}: ConfirmDialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  // Quien abrió el diálogo recupera el foco al cerrarlo (WCAG 2.4.3). Si el llamador fija
  // otro destino, su efecto corre después y prevalece.
  const opener = useRef<HTMLElement | null>(null);

  const restoreFocus = useCallback(() => {
    const target = opener.current;
    opener.current = null;
    if (target && target !== document.body && document.contains(target)) {
      target.focus();
    }
  }, []);

  useEffect(() => restoreFocus, [restoreFocus]);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) {
      return;
    }
    if (open && !dialog.open) {
      opener.current =
        document.activeElement instanceof HTMLElement ? document.activeElement : null;
      // jsdom no implementa showModal; en el navegador sí existe.
      if (typeof dialog.showModal === 'function') {
        dialog.showModal();
      } else {
        dialog.setAttribute('open', '');
      }
    } else if (!open && dialog.open) {
      if (typeof dialog.close === 'function') {
        dialog.close();
      } else {
        dialog.removeAttribute('open');
      }
      restoreFocus();
    }
  }, [open, restoreFocus]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      onCancel={(event) => {
        event.preventDefault();
        // Mientras se guarda, Esc no puede cerrar el diálogo: reabriría el botón de guardar.
        if (!busy) {
          onCancel();
        }
      }}
      className={`m-auto rounded-xl bg-surface p-6 text-ink backdrop:bg-ink/50 ${wide ? 'max-h-[90vh] w-full max-w-3xl overflow-y-auto' : 'max-w-md'}`}
    >
      <h2 id={titleId} className="text-lg font-semibold">
        {title}
      </h2>
      <div id={descriptionId} className="mt-2 text-ink-muted">
        {children}
      </div>
      <div className="mt-6 flex flex-wrap justify-end gap-3">
        {cancelLabel && (
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </Button>
        )}
        <Button onClick={onConfirm} disabled={busy} aria-busy={busy || undefined}>
          {confirmLabel}
        </Button>
      </div>
    </dialog>
  );
}
