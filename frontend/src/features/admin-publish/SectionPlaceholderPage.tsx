import { Link, useParams } from 'react-router';
import { QuestionnaireTabs } from '../../components/QuestionnaireTabs';
import { useDocumentTitle } from '../../hooks/use-document-title';

interface SectionPlaceholderPageProps {
  section: string;
}

/** Sección del cuestionario que aún no está construida: conserva la navegación del año. */
export function SectionPlaceholderPage({ section }: SectionPlaceholderPageProps) {
  const { encuesta = '', anio = '' } = useParams();
  const title = `${section} ${encuesta} ${anio}`;
  useDocumentTitle(title);
  return (
    <section aria-labelledby="titulo-seccion">
      <Link
        to="/admin/cuestionario"
        className="inline-flex min-h-11 items-center text-primary underline"
      >
        Volver a los años
      </Link>
      <h1 id="titulo-seccion" className="text-2xl font-semibold">
        {title}
      </h1>
      <QuestionnaireTabs survey={encuesta} year={Number(anio)} />
      <p className="mt-6">Disponible próximamente.</p>
    </section>
  );
}
