import { forwardRef } from 'react';
import type { ImportPreview } from './packages-api';
import { ScrollableRegion } from '../../components/ScrollableRegion';

interface ImportPreviewViewProps {
  preview: ImportPreview;
}

/** Resultado de verificar un paquete: región enfocable con el impacto por página. */
export const ImportPreviewView = forwardRef<HTMLElement, ImportPreviewViewProps>(
  function ImportPreviewView({ preview }, ref) {
    const impact = preview.impact ?? [];
    return (
      <section
        ref={ref}
        tabIndex={-1}
        aria-label="Resultado de la verificación"
        className="mt-6 rounded-xl border border-ink-muted/30 p-4"
      >
        <p className="font-semibold text-success">
          El paquete pasó la verificación (versión {preview.version}).
        </p>
        {preview.alreadyImported && <p className="mt-2">El paquete ya estaba importado.</p>}
        {preview.overwritesLocalChange && (
          <p className="mt-2 text-danger">
            Importar reemplaza un cambio hecho en este ambiente después de la versión del paquete.
          </p>
        )}
        {impact.length === 0 ? (
          <p className="mt-2">Ninguna página guardada cambia con este paquete.</p>
        ) : (
          <ScrollableRegion label="Impacto por página" className="mt-3">
            <table className="w-full border-collapse text-left">
              <caption className="mb-2 text-left font-semibold">Impacto por página</caption>
              <thead>
                <tr className="border-b border-ink-muted/40">
                  <th scope="col" className="p-2">
                    Página
                  </th>
                  <th scope="col" className="p-2">
                    Impacto
                  </th>
                </tr>
              </thead>
              <tbody>
                {impact.map((page) => (
                  <tr key={page.pageCode} className="border-b border-ink-muted/20">
                    <th scope="row" className="p-2 font-medium">
                      {page.pageCode}
                    </th>
                    <td className="p-2">
                      {page.savedCount} encuestados ya guardaron esta página con la regla anterior.
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </ScrollableRegion>
        )}
      </section>
    );
  },
);
