import { useEffect, useRef } from 'react';
import { Button } from './Button';

interface ConflictNoticeProps {
  message: string;
  onReload: () => void;
  /** Lleva el foco al aviso al mostrarse (cuando quien lo causó ya no está en pantalla). */
  focusOnShow?: boolean;
}

/** Aviso de conflicto 409 (otro usuario cambió el año o ya está publicado), con "Recargar". */
export function ConflictNotice({ message, onReload, focusOnShow = false }: ConflictNoticeProps) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (focusOnShow) {
      ref.current?.focus();
    }
  }, [focusOnShow, message]);
  return (
    <div
      ref={ref}
      role="alert"
      tabIndex={focusOnShow ? -1 : undefined}
      className="mt-6 rounded-xl border border-danger/40 bg-surface p-4"
    >
      <p className="text-danger">{message}</p>
      <Button variant="secondary" className="mt-3" onClick={onReload}>
        Recargar
      </Button>
    </div>
  );
}
