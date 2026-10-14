import type { ReactNode } from 'react';
import { Outlet } from 'react-router';
import { AppHeader } from './AppHeader';

interface AppLayoutProps {
  headerActions?: ReactNode;
}

export function AppLayout({ headerActions }: AppLayoutProps) {
  return (
    <div className="flex min-h-dvh flex-col bg-surface-muted font-sans text-ink">
      <a
        href="#contenido"
        className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-50 focus:rounded-md focus:bg-surface focus:px-4 focus:py-2 focus:text-primary"
      >
        Saltar al contenido
      </a>
      <AppHeader actions={headerActions} />
      <main id="contenido" tabIndex={-1} className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="border-t border-ink-muted/20 py-6 text-center text-sm text-ink-muted">
        DANE · Encuesta sobre Ambiente y Desempeño Institucional
      </footer>
    </div>
  );
}
