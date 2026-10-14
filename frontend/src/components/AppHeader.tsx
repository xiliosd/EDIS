import type { ReactNode } from 'react';

interface AppHeaderProps {
  actions?: ReactNode;
}

export function AppHeader({ actions }: AppHeaderProps) {
  return (
    <header className="border-b-4 border-primary bg-surface">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-4 px-4 py-3">
        {/* El cabezote es un JPEG con fondo blanco: conserva su propio respaldo en modo oscuro. */}
        <div className="w-full max-w-3xl rounded-lg bg-white p-2">
          <img
            src="/brand/cabezote-edi-edid.jpeg"
            alt="EDI-EDID, Encuesta sobre Ambiente y Desempeño Institucional Nacional y Departamental. DANE"
            width={900}
            height={123}
            className="h-auto w-full"
          />
        </div>
        {actions}
      </div>
    </header>
  );
}
