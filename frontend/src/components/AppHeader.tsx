import type { ReactNode } from 'react';

interface AppHeaderProps {
  actions?: ReactNode;
}

export function AppHeader({ actions }: AppHeaderProps) {
  return (
    <header className="border-b-4 border-primary bg-white">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-4 px-4 py-3">
        <img
          src="/brand/cabezote-edi-edid.jpeg"
          alt="EDI-EDID, Encuesta sobre Ambiente y Desempeño Institucional Nacional y Departamental. DANE"
          width={902}
          height={125}
          className="h-auto w-full max-w-3xl"
        />
        {actions}
      </div>
    </header>
  );
}
