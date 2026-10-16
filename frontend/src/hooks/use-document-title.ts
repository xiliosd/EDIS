import { useEffect } from 'react';

const APP_NAME = 'Encuesta EDI';

/** Cada vista anuncia su título en la pestaña y al lector de pantalla (WCAG 2.4.2). */
export function useDocumentTitle(title: string): void {
  useEffect(() => {
    document.title = title ? `${title} · ${APP_NAME}` : APP_NAME;
  }, [title]);
}
