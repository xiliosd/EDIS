import { Link } from 'react-router';
import { ApiError } from '../../services/api-error';

export type SessionErrorStatus = 401 | 403;

const MESSAGES: Record<SessionErrorStatus, string> = {
  401: 'Su sesión venció. Vuelva a ingresar; las respuestas de esta página no se guardaron.',
  403: 'La sesión de seguridad cambió. Recargue la página e intente de nuevo.',
};

/** 401: la sesión venció; 403: el token CSRF ya no corresponde a la sesión (spec §7.3). */
export function sessionErrorStatus(error: unknown): SessionErrorStatus | null {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
    return error.status;
  }
  return null;
}

export function SessionErrorAlert({ status }: { status: SessionErrorStatus }) {
  return (
    <div role="alert" className="mt-6 rounded-xl border border-danger/40 bg-surface p-4">
      <p className="text-danger">{MESSAGES[status]}</p>
      {status === 401 && (
        <Link to="/ingresar" className="mt-2 inline-block text-primary underline">
          Ingresar de nuevo
        </Link>
      )}
    </div>
  );
}
