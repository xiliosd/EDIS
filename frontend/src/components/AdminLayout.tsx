import { NavLink, Outlet } from 'react-router';
import { hasAnyRole, useSession } from '../features/auth/use-session';

const LINK_CLASSES =
  'flex min-h-11 items-center rounded-lg px-3 font-medium text-ink hover:bg-primary/10';
const ACTIVE_LINK_CLASSES = 'bg-primary/10 text-primary underline';

function menuLinkClass({ isActive }: { isActive: boolean }) {
  return `${LINK_CLASSES} ${isActive ? ACTIVE_LINK_CLASSES : ''}`;
}

/** Marco de las pantallas de administración: menú lateral y el contenido de la ruta hija. */
export function AdminLayout() {
  const { data: session } = useSession();
  const canEditQuestionnaire = hasAnyRole(session, ['ADMIN_CUESTIONARIO']);

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
        </ul>
      </nav>
      <div>
        <Outlet />
      </div>
    </div>
  );
}
