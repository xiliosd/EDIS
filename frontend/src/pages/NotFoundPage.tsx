import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <section aria-labelledby="titulo-no-encontrada" className="rounded-2xl bg-surface p-8">
      <h1 id="titulo-no-encontrada" className="text-2xl font-semibold">
        Página no encontrada
      </h1>
      <p className="mt-2 text-ink-muted">La dirección que abrió no existe o cambió.</p>
      <Link
        to="/"
        className="mt-6 inline-block font-medium text-primary underline underline-offset-4"
      >
        Ir al inicio
      </Link>
    </section>
  );
}
