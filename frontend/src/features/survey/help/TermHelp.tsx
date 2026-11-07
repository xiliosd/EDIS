import { useState, type KeyboardEvent, type MouseEvent, type ReactNode } from 'react';
import { Button } from '../../../components/Button';
import type { components } from '../../../services/api-schema';
import { splitByTerms } from './term-segments';

export type TermHelpItem = components['schemas']['TermHelp'];

interface TermHelpOptions {
  /** Prefijo único en la página para los id de botones y paneles. */
  idPrefix: string;
  text: string;
  help?: TermHelpItem[] | null;
  /** Posición desde la que se busca el término (deja fuera el numeral). */
  searchFrom?: number;
}

export interface TermHelpParts {
  /** El texto con el término como botón; va dentro del label, legend o encabezado. */
  content: ReactNode;
  /** Botones «Ayuda: …» y paneles; van justo después, fuera del label o legend. */
  details: ReactNode;
  /**
   * Con ayudas, el nombre accesible del campo: el texto completo tal cual. Así ni el botón del
   * término ni el espacio que algunos navegadores agregan alrededor de él cambian cómo el lector
   * de pantalla nombra la pregunta. Sin ayudas no hace falta (undefined).
   */
  accessibleName?: string;
}

// El relleno vertical da un área de toque de al menos 24 px; el margen negativo no mueve la línea.
const TERM_CLASSES =
  '-my-1 cursor-pointer rounded-sm px-0.5 py-1 text-primary underline underline-offset-2 hover:text-primary-strong';

const EXTRA_BUTTON_CLASSES =
  'inline-flex min-h-11 cursor-pointer items-center text-primary underline underline-offset-2 hover:text-primary-strong';

function buttonIdOf(idPrefix: string, index: number) {
  return `${idPrefix}-ayuda-${index}-boton`;
}

function panelIdOf(idPrefix: string, index: number) {
  return `${idPrefix}-ayuda-${index}`;
}

/**
 * Ayudas del texto de una pregunta o encabezado, como los enlaces del formulario heredado: el
 * término abre la definición debajo del texto (no en una ventana flotante, que en el celular tapa
 * la pregunta). Cada ayuda abre y cierra por separado.
 */
export function useTermHelp({
  idPrefix,
  text,
  help,
  searchFrom = 0,
}: TermHelpOptions): TermHelpParts {
  const [openHelp, setOpenHelp] = useState<number[]>([]);
  const items = help ?? [];
  if (items.length === 0) {
    return { content: text, details: null, accessibleName: undefined };
  }
  const { segments, missing } = splitByTerms(
    text,
    items.map((item) => item.term ?? ''),
    searchFrom,
  );

  function toggle(index: number) {
    setOpenHelp((current) =>
      current.includes(index) ? current.filter((open) => open !== index) : [...current, index],
    );
  }

  function close(index: number) {
    setOpenHelp((current) => current.filter((open) => open !== index));
    document.getElementById(buttonIdOf(idPrefix, index))?.focus();
  }

  function closeOnEscape(index: number) {
    return (event: KeyboardEvent<HTMLButtonElement>) => {
      if (event.key === 'Escape' && openHelp.includes(index)) {
        event.preventDefault();
        close(index);
      }
    };
  }

  function termButton(index: number, label: string, className: string) {
    return (
      <button
        key={`ayuda-${index}`}
        type="button"
        id={buttonIdOf(idPrefix, index)}
        aria-expanded={openHelp.includes(index)}
        aria-controls={panelIdOf(idPrefix, index)}
        className={className}
        onClick={(event: MouseEvent<HTMLButtonElement>) => {
          // Dentro de un <label>, el clic no debe marcar ni enfocar la respuesta.
          event.preventDefault();
          event.stopPropagation();
          toggle(index);
        }}
        onKeyDown={closeOnEscape(index)}
      >
        {label}
      </button>
    );
  }

  const content = segments.map((segment) =>
    segment.kind === 'text'
      ? segment.text
      : termButton(segment.helpIndex, segment.text, TERM_CLASSES),
  );

  const details = (
    <>
      {missing.length > 0 && (
        <p className="mt-1 flex flex-wrap gap-x-4 text-base font-normal">
          {missing.map((index) =>
            termButton(index, `Ayuda: ${items[index].term ?? ''}`, EXTRA_BUTTON_CLASSES),
          )}
        </p>
      )}
      {items.map((item, index) => (
        <div
          key={index}
          id={panelIdOf(idPrefix, index)}
          hidden={!openHelp.includes(index)}
          className="mt-2 rounded-lg border border-primary/40 bg-surface-muted p-3 text-base font-normal text-ink"
        >
          <p className="font-semibold">{item.title || item.term}</p>
          {item.definition && <p className="mt-1 whitespace-pre-line">{item.definition}</p>}
          <Button
            variant="secondary"
            className="mt-3"
            onClick={() => close(index)}
            onKeyDown={closeOnEscape(index)}
          >
            Cerrar ayuda
          </Button>
        </div>
      ))}
    </>
  );
  return { content, details, accessibleName: text };
}

interface HelpTextProps extends TermHelpOptions {
  as: 'h2' | 'h3' | 'p';
  className?: string;
}

/** Encabezado o enunciado con sus ayudas: el texto y, debajo, los paneles. */
export function HelpText({ as: Tag, className, ...options }: HelpTextProps) {
  const { content, details } = useTermHelp(options);
  return (
    <>
      <Tag className={className}>{content}</Tag>
      {details}
    </>
  );
}
