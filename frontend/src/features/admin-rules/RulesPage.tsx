import { Link, useParams } from 'react-router';
import { QuestionnaireTabs } from '../../components/QuestionnaireTabs';
import { useDocumentTitle } from '../../hooks/use-document-title';

/** Marcador de la sección de reglas; la Tarea 8 lo reemplaza por el editor. */
export function RulesPage() {
  const { encuesta = '', anio = '' } = useParams();
  const title = `Reglas ${encuesta} ${anio}`;
  useDocumentTitle(title);
  return (
    <section aria-labelledby="titulo-reglas">
      <Link
        to="/admin/cuestionario"
        className="inline-flex min-h-11 items-center text-primary underline"
      >
        Volver a los años
      </Link>
      <h1 id="titulo-reglas" className="text-2xl font-semibold">
        {title}
      </h1>
      <QuestionnaireTabs survey={encuesta} year={Number(anio)} />
      <p className="mt-6 text-ink-muted">El editor de reglas estará disponible pronto.</p>
    </section>
  );
}
