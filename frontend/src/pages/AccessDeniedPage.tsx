export function AccessDeniedPage() {
  return (
    <section aria-labelledby="titulo-acceso-denegado" className="rounded-2xl bg-surface p-8">
      <h1 id="titulo-acceso-denegado" className="text-2xl font-semibold">
        Acceso denegado
      </h1>
      <p className="mt-2 text-ink-muted">Su usuario no tiene permisos para ver esta sección.</p>
    </section>
  );
}
