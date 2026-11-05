import { forwardRef } from 'react';
import { Link } from 'react-router';
import type { ChecklistItem, PublicationChecklist } from './publish-api';

interface ChecklistViewProps {
  checklist: PublicationChecklist;
  survey: string;
  year: number;
  /** Avisos que se listan: los de la lista viva, o los registrados si el año ya se publicó. */
  warnings: string[];
}

interface ElementLink {
  label: string;
  to: string;
}

/**
 * Cada condición señala sus elementos en un formato propio: el análisis usa `tipo:id`, los casos
 * llegan como id, las columnas como código y las tablas como código de capítulo.
 */
function linkOf(code: string, element: string, base: string): ElementLink | null {
  if (code === 'CASOS_EN_VERDE') {
    return { label: `Ir al caso ${element}`, to: `${base}/simulador#caso-${element}` };
  }
  if (code === 'COLUMNAS_CREADAS') {
    // El script de la columna se descarga en la pregunta, dentro del árbol de Preguntas.
    return { label: `Ir a la columna ${element}`, to: base };
  }
  if (code === 'TABLAS_VALIDAS') {
    return {
      label: `Ir al panel de tablas del capítulo ${element}`,
      to: `${base}#titulo-tablas`,
    };
  }
  const separator = element.indexOf(':');
  const kind = element.slice(0, separator);
  const id = element.slice(separator + 1);
  if (separator < 0 || !id) {
    return null;
  }
  if (kind === 'regla') {
    return { label: `Ir a la regla ${id}`, to: `${base}/reglas#regla-${id}` };
  }
  if (kind === 'pregunta' || kind === 'validacion') {
    return { label: `Ir a la pregunta ${id}`, to: `${base}/reglas#pregunta-${id}` };
  }
  if (kind === 'caso') {
    return { label: `Ir al caso ${id}`, to: `${base}/simulador#caso-${id}` };
  }
  return null;
}

function Item({ item, base }: { item: ChecklistItem; base: string }) {
  const passed = item.passed === true;
  const elements = item.elements ?? [];
  return (
    <li className="rounded-xl border border-ink-muted/30 p-4">
      <p className={`font-medium ${passed ? 'text-success' : 'text-danger'}`}>
        <span aria-hidden="true">{passed ? '✓' : '✗'}</span> {passed ? 'Cumple' : 'No cumple'}
      </p>
      <p className="mt-1">{item.detail}</p>
      {elements.length > 0 && (
        <ul className="mt-2 flex flex-wrap gap-x-4 gap-y-1">
          {elements.map((element) => {
            const link = linkOf(item.code ?? '', element, base);
            return (
              <li key={element}>
                {link ? (
                  <Link
                    to={link.to}
                    className="inline-flex min-h-11 items-center text-primary underline"
                  >
                    {link.label}
                  </Link>
                ) : (
                  <span>{element}</span>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </li>
  );
}

/** Resumen de la lista de publicación; es una región enfocable para anunciar un 422. */
export const ChecklistView = forwardRef<HTMLElement, ChecklistViewProps>(function ChecklistView(
  { checklist, survey, year, warnings },
  ref,
) {
  const base = `/admin/cuestionario/${survey}/${year}`;
  return (
    <section
      ref={ref}
      tabIndex={-1}
      aria-label="Resumen de la lista de publicación"
      className="mt-6"
    >
      <p className={`text-lg font-semibold ${checklist.ready ? 'text-success' : 'text-danger'}`}>
        {checklist.ready
          ? 'La lista está completa: el año puede publicarse.'
          : 'La lista está incompleta: corrija lo siguiente para publicar.'}
      </p>
      <ul aria-label="Condiciones para publicar" className="mt-3 grid gap-3">
        {(checklist.items ?? []).map((item, index) => (
          <Item key={`${item.code}-${index}`} item={item} base={base} />
        ))}
      </ul>
      {warnings.length > 0 && (
        <div className="mt-4">
          <h2 className="text-lg font-semibold">Avisos (no bloquean la publicación)</h2>
          <ul className="mt-2 list-disc pl-6">
            {warnings.map((warning, index) => (
              <li key={`${warning}-${index}`}>{warning}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
});
