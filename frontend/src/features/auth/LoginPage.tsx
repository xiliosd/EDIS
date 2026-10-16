import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useLocation, useNavigate } from 'react-router';
import { Button } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { useDocumentTitle } from '../../hooks/use-document-title';
import { ApiError } from '../../services/api-error';
import { SESSION_QUERY_KEY } from '../../services/query-client';
import { bindDraftsToUser } from '../survey/page-drafts';
import { login, type LoginRequest } from './auth-api';
import { homePathFor } from './use-session';

interface LocationState {
  from?: unknown;
  logoutFailed?: boolean;
}

/** Solo rutas internas: evita redirecciones abiertas hacia otros sitios. */
function toInternalPath(value: unknown): string | undefined {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')
    ? value
    : undefined;
}

const ACCOUNT_TYPES: ReadonlyArray<{ value: LoginRequest['accountType']; label: string }> = [
  { value: 'RESPONDENT', label: 'Servidor(a) público(a)' },
  { value: 'ADMINISTRATOR', label: 'Administrador(a)' },
];

export function LoginPage() {
  useDocumentTitle('Ingreso a la encuesta');
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const locationState = location.state as LocationState | null;
  const redirectTo = toInternalPath(locationState?.from);
  const logoutFailed = locationState?.logoutFailed === true;
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginRequest>({
    defaultValues: { accountType: 'RESPONDENT', username: '', password: '' },
  });

  async function onSubmit(values: LoginRequest) {
    setFormError(null);
    try {
      const session = await login(values);
      // Los borradores de otro usuario en esta pestaña se descartan antes de entrar.
      await bindDraftsToUser(values.accountType, values.username);
      queryClient.setQueryData(SESSION_QUERY_KEY, session);
      navigate(redirectTo ?? homePathFor(session), { replace: true });
    } catch (error) {
      setFormError(error instanceof ApiError ? error.detail : 'No fue posible iniciar sesión.');
    }
  }

  return (
    <section
      aria-labelledby="titulo-ingreso"
      className="mx-auto max-w-md rounded-2xl bg-surface p-6 shadow-sm sm:p-8"
    >
      <h1 id="titulo-ingreso" className="text-2xl font-semibold">
        Ingreso a la encuesta
      </h1>
      <p className="mt-2 text-ink-muted">Use el usuario y la clave que recibió del DANE.</p>
      {redirectTo && (
        <p role="status" className="mt-4 rounded-lg bg-surface-muted p-3 text-sm">
          Ingrese para continuar.
        </p>
      )}
      {logoutFailed && (
        <p role="alert" className="mt-4 rounded-lg border border-danger/40 p-3 text-danger">
          No se pudo cerrar la sesión en el servidor. Cierre el navegador si usa un equipo
          compartido.
        </p>
      )}
      {formError && (
        <p role="alert" className="mt-4 rounded-lg border border-danger/40 p-3 text-danger">
          {formError}
        </p>
      )}
      <form noValidate onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-5">
        <fieldset>
          <legend className="text-sm font-medium">Tipo de usuario</legend>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            {ACCOUNT_TYPES.map((option) => (
              <label
                key={option.value}
                className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-ink-muted/40 px-3 has-[:checked]:border-primary has-[:checked]:bg-primary/10"
              >
                <input type="radio" value={option.value} {...register('accountType')} />
                {option.label}
              </label>
            ))}
          </div>
        </fieldset>
        <TextField
          id="usuario"
          label="Usuario"
          autoComplete="username"
          error={errors.username?.message}
          {...register('username', { required: 'Ingrese su usuario' })}
        />
        <TextField
          id="clave"
          label="Clave"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password', { required: 'Ingrese su clave' })}
        />
        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Ingresando…' : 'Ingresar'}
        </Button>
      </form>
    </section>
  );
}
