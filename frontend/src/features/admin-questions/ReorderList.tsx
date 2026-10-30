import { useEffect, useId, useState, type DragEvent, type ReactNode } from 'react';

interface ReorderListProps<T> {
  /** Nombre accesible de la lista, p. ej. "Preguntas de la página C1". */
  label: string;
  items: T[];
  getId: (item: T) => number;
  /** Nombre corto del elemento en botones y anuncios ("Subir C07A"). */
  getLabel: (item: T) => string;
  renderItem: (item: T) => ReactNode;
  /** Sin edición la lista se muestra igual pero sin arrastre ni botones de orden. */
  editable: boolean;
  /** Guarda el orden pedido; resuelve true si el servidor lo aceptó. */
  onReorder: (ids: number[]) => Promise<boolean>;
}

type Direction = 'up' | 'down';

function moveItem(ids: number[], from: number, to: number): number[] {
  const next = [...ids];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

/**
 * Lista ordenable con arrastrar y soltar y, como alternativa de teclado (WCAG 2.1.1), botones
 * "Subir"/"Bajar" por elemento. El nuevo puesto se anuncia en una región aria-live.
 */
export function ReorderList<T>({
  label,
  items,
  getId,
  getLabel,
  renderItem,
  editable,
  onReorder,
}: ReorderListProps<T>) {
  const idPrefix = useId();
  const [busy, setBusy] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  const [draggedId, setDraggedId] = useState<number | null>(null);
  const [dropTargetId, setDropTargetId] = useState<number | null>(null);
  const [focusAfterMove, setFocusAfterMove] = useState<{ id: number; direction: Direction }>();
  const ids = items.map(getId);
  const count = items.length;

  function buttonId(id: number, direction: Direction) {
    return `${idPrefix}-${direction}-${id}`;
  }

  // Tras mover, el foco sigue en el botón usado; si quedó deshabilitado (borde), pasa al otro.
  useEffect(() => {
    if (!focusAfterMove || busy) {
      return;
    }
    const { id, direction } = focusAfterMove;
    const same = document.getElementById(buttonId(id, direction)) as HTMLButtonElement | null;
    const other = document.getElementById(
      buttonId(id, direction === 'up' ? 'down' : 'up'),
    ) as HTMLButtonElement | null;
    (same && !same.disabled ? same : other)?.focus();
    setFocusAfterMove(undefined);
    // buttonId solo depende de idPrefix, que es estable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusAfterMove, busy, items]);

  async function move(id: number, to: number, direction?: Direction) {
    const from = ids.indexOf(id);
    if (busy || from < 0 || to < 0 || to >= count || from === to) {
      return;
    }
    setBusy(true);
    setAnnouncement('');
    const accepted = await onReorder(moveItem(ids, from, to));
    setBusy(false);
    if (accepted) {
      const item = items[from];
      setAnnouncement(`${getLabel(item)} ahora es la pregunta ${to + 1} de ${count}`);
      if (direction) {
        setFocusAfterMove({ id, direction });
      }
    }
  }

  function handleDrop(event: DragEvent<HTMLLIElement>, targetId: number) {
    event.preventDefault();
    const moved = draggedId;
    setDraggedId(null);
    setDropTargetId(null);
    if (moved !== null) {
      void move(moved, ids.indexOf(targetId));
    }
  }

  return (
    <>
      <ol aria-label={label} className="mt-4 grid gap-3">
        {items.map((item, index) => {
          const id = getId(item);
          const itemLabel = getLabel(item);
          const isDropTarget = dropTargetId === id && draggedId !== id;
          return (
            <li
              key={id}
              aria-label={`Pregunta ${itemLabel}`}
              onDragOver={(event) => {
                if (draggedId !== null) {
                  event.preventDefault();
                  setDropTargetId(id);
                }
              }}
              onDragLeave={() => setDropTargetId((current) => (current === id ? null : current))}
              onDrop={(event) => handleDrop(event, id)}
              className={`flex gap-3 rounded-xl border bg-surface p-4 ${
                isDropTarget ? 'border-primary border-dashed' : 'border-ink-muted/30'
              } ${draggedId === id ? 'opacity-60' : ''}`}
            >
              {editable && (
                <div className="flex shrink-0 flex-col items-center gap-2">
                  <span
                    draggable={!busy}
                    title={`Arrastrar ${itemLabel}`}
                    aria-hidden="true"
                    onDragStart={(event) => {
                      // Firefox solo inicia el arrastre si hay datos en el DataTransfer.
                      event.dataTransfer?.setData('text/plain', String(id));
                      setDraggedId(id);
                    }}
                    onDragEnd={() => {
                      setDraggedId(null);
                      setDropTargetId(null);
                    }}
                    className="flex min-h-11 min-w-11 cursor-grab items-center justify-center rounded-lg text-xl text-ink-muted hover:bg-primary/10"
                  >
                    ⠿
                  </span>
                  <button
                    type="button"
                    id={buttonId(id, 'up')}
                    aria-label={`Subir ${itemLabel}`}
                    disabled={busy || index === 0}
                    onClick={() => void move(id, index - 1, 'up')}
                    className="min-h-11 min-w-11 rounded-lg border border-primary px-2 text-sm text-primary hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    Subir
                  </button>
                  <button
                    type="button"
                    id={buttonId(id, 'down')}
                    aria-label={`Bajar ${itemLabel}`}
                    disabled={busy || index === count - 1}
                    onClick={() => void move(id, index + 1, 'down')}
                    className="min-h-11 min-w-11 rounded-lg border border-primary px-2 text-sm text-primary hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    Bajar
                  </button>
                </div>
              )}
              <div className="min-w-0 flex-1">{renderItem(item)}</div>
            </li>
          );
        })}
      </ol>
      <p aria-live="polite" className="sr-only">
        {announcement}
      </p>
    </>
  );
}
