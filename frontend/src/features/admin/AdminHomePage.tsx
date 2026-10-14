const UPCOMING_MODULES = [
  { name: 'Cuestionario', description: 'Textos, preguntas y orden.' },
  { name: 'Reglas', description: 'Saltos y valores mínimos y máximos.' },
  { name: 'Reportes', description: 'Cobertura, estado operativo y exportaciones.' },
  { name: 'Novedades', description: 'Novedades por funcionario.' },
];

export function AdminHomePage() {
  return (
    <section aria-labelledby="titulo-admin">
      <h1 id="titulo-admin" className="text-2xl font-semibold">
        Administración
      </h1>
      <p className="mt-2 text-ink-muted">Estos módulos se habilitan en las próximas entregas.</p>
      <ul className="mt-6 grid gap-3 sm:grid-cols-2">
        {UPCOMING_MODULES.map((module) => (
          <li key={module.name} className="rounded-xl border border-ink-muted/20 bg-surface p-4">
            <p className="font-medium">{module.name}</p>
            <p className="text-sm text-ink-muted">{module.description}</p>
          </li>
        ))}
      </ul>
    </section>
  );
}
