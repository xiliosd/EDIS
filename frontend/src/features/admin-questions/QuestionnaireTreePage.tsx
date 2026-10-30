import { useParams } from 'react-router';
import { useDocumentTitle } from '../../hooks/use-document-title';

/** Marcador de la ruta del árbol; la edición del cuestionario llega en la siguiente tarea. */
export function QuestionnaireTreePage() {
  const { encuesta, anio } = useParams();
  const title = `Cuestionario ${encuesta} ${anio}`;
  useDocumentTitle(title);
  return (
    <section aria-labelledby="titulo-arbol">
      <h1 id="titulo-arbol" className="text-2xl font-semibold">
        {title}
      </h1>
    </section>
  );
}
