import { NavLink } from 'react-router';

interface QuestionnaireTabsProps {
  survey: string;
  year: number;
}

const LINK_CLASSES =
  'inline-flex min-h-11 items-center rounded-lg px-4 font-medium text-ink hover:bg-primary/10';
const ACTIVE_LINK_CLASSES = 'bg-primary/10 text-primary underline';

function tabClass({ isActive }: { isActive: boolean }) {
  return `${LINK_CLASSES} ${isActive ? ACTIVE_LINK_CLASSES : ''}`;
}

/** Secciones de un cuestionario por año: árbol de preguntas, reglas, simulador, publicación, historial y paquetes. */
export function QuestionnaireTabs({ survey, year }: QuestionnaireTabsProps) {
  const base = `/admin/cuestionario/${survey}/${year}`;
  return (
    <nav aria-label="Secciones del cuestionario" className="mt-4">
      <ul className="flex flex-wrap gap-1">
        <li>
          <NavLink to={base} end className={tabClass}>
            Preguntas
          </NavLink>
        </li>
        <li>
          <NavLink to={`${base}/reglas`} className={tabClass}>
            Reglas
          </NavLink>
        </li>
        <li>
          <NavLink to={`${base}/simulador`} className={tabClass}>
            Simulador
          </NavLink>
        </li>
        <li>
          <NavLink to={`${base}/publicar`} className={tabClass}>
            Publicar
          </NavLink>
        </li>
        <li>
          <NavLink to={`${base}/historial`} className={tabClass}>
            Historial
          </NavLink>
        </li>
        <li>
          <NavLink to={`${base}/paquetes`} className={tabClass}>
            Paquetes
          </NavLink>
        </li>
      </ul>
    </nav>
  );
}
