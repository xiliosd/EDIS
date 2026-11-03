import { Link, useParams } from 'react-router';
import { QuestionnaireTabs } from '../../components/QuestionnaireTabs';
import { useDocumentTitle } from '../../hooks/use-document-title';

/** Marcador de la sección del simulador; la Tarea 9 lo reemplaza por el simulador. */
export function SimulatorPage() {
  const { encuesta = '', anio = '' } = useParams();
  const title = `Simulador ${encuesta} ${anio}`;
  useDocumentTitle(title);
  return (
    <section aria-labelledby="titulo-simulador">
      <Link
        to="/admin/cuestionario"
        className="inline-flex min-h-11 items-center text-primary underline"
      >
        Volver a los años
      </Link>
      <h1 id="titulo-simulador" className="text-2xl font-semibold">
        {title}
      </h1>
      <QuestionnaireTabs survey={encuesta} year={Number(anio)} />
      <p className="mt-6 text-ink-muted">El simulador estará disponible pronto.</p>
    </section>
  );
}
