import { Button } from '../../components/Button';

interface ReportPagerProps {
  /** Página actual, desde 0. */
  page: number;
  pageCount: number;
  total: number;
  onChange: (page: number) => void;
}

/** Controles Anterior/Siguiente con la posición anunciada al lector de pantalla. */
export function ReportPager({ page, pageCount, total, onChange }: ReportPagerProps) {
  return (
    <nav aria-label="Paginación" className="mt-3 flex flex-wrap items-center gap-3">
      <Button variant="secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Anterior
      </Button>
      <span role="status" aria-live="polite">
        Página {page + 1} de {Math.max(pageCount, 1)} ({total.toLocaleString('es-CO')} registros)
      </span>
      <Button
        variant="secondary"
        disabled={page + 1 >= pageCount}
        onClick={() => onChange(page + 1)}
      >
        Siguiente
      </Button>
    </nav>
  );
}
