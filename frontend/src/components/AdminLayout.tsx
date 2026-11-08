import { NavLink, Outlet } from 'react-router';
import { OPERATION_ROLES, useOperationScope } from '../features/admin/operation-scope';
import { hasAnyRole, useSession } from '../features/auth/use-session';

const LINK_CLASSES =
  'flex min-h-11 items-center rounded-lg px-3 font-medium text-ink hover:bg-primary/10';
const ACTIVE_LINK_CLASSES = 'bg-primary/10 text-primary underline';

const REPORT_LINKS = [
  { to: '/admin/reportes/cobertura', label: 'Cobertura' },
  { to: '/admin/reportes/cortes', label: 'Cortes e histórico' },
  { to: '/admin/reportes/estado', label: 'Estado del operativo' },
  { to: '/admin/reportes/novedades', label: 'Novedades' },
  { to: '/admin/reportes/rendimientos', label: 'Rendimientos' },
] as const;

function menuLinkClass({ isActive }: { isActive: boolean }) {
  return `${LINK_CLASSES} ${isActive ? ACTIVE_LINK_CLASSES : ''}`;
}

/** Marco de las pantallas de administración: menú lateral y el contenido de la ruta hija. */
export function AdminLayout() {
  const { data: session } = useSession();
  const canEditQuestionnaire = hasAnyRole(session, ['ADMIN_CUESTIONARIO']);
  const canSeeReports = hasAnyRole(session, OPERATION_ROLES);
  const { data: scope } = useOperationScope();

  return (
    <div className="grid gap-6 md:grid-cols-[14rem_1fr]">
      <nav aria-label="Administración">
        <ul className="flex flex-wrap gap-1 md:flex-col">
          <li>
            <NavLink to="/admin" end className={menuLinkClass}>
              Inicio
            </NavLink>
          </li>
          {canEditQuestionnaire && (
            <li>
              <NavLink to="/admin/cuestionario" className={menuLinkClass}>
                Cuestionario
              </NavLink>
            </li>
          )}
          {canSeeReports && (
            <li>
              <p className="px-3 pt-2 text-sm font-semibold text-ink-muted" id="menu-reportes">
                Reportes
              </p>
              <ul aria-labelledby="menu-reportes" className="flex flex-wrap gap-1 md:flex-col">
                {REPORT_LINKS.map((link) => (
                  <li key={link.to}>
                    <NavLink to={link.to} className={menuLinkClass}>
                      {link.label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </li>
          )}
          {scope?.canManageNovelties && (
            <li>
              <NavLink to="/admin/novedades" className={menuLinkClass}>
                Gestión de novedades
              </NavLink>
            </li>
          )}
          {scope?.canManageKeys && (
            <li>
              <NavLink to="/admin/claves" className={menuLinkClass}>
                Claves
              </NavLink>
            </li>
          )}
        </ul>
      </nav>
      {/* min-w-0: sin él, una tabla ancha estira la columna de la rejilla y la página en móvil. */}
      <div className="min-w-0">
        <Outlet />
      </div>
    </div>
  );
}
