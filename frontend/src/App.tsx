import { lazy, Suspense, useEffect } from 'react';
import { Route, Routes } from 'react-router';
import { AdminLayout } from './components/AdminLayout';
import { AppLayout } from './components/AppLayout';
import { SessionMenu } from './components/SessionMenu';
import { AdminHomePage } from './features/admin/AdminHomePage';
import { OPERATION_ROLES } from './features/admin/operation-scope';
import { HistoryPage } from './features/admin-history/HistoryPage';
import { PackagesPage } from './features/admin-packages/PackagesPage';
import { PublishPage } from './features/admin-publish/PublishPage';
import { CoveragePage } from './features/admin-reports/CoveragePage';
import { CutsPage } from './features/admin-reports/CutsPage';
import { QuestionnaireTreePage } from './features/admin-questions/QuestionnaireTreePage';
import { YearsPage } from './features/admin-questions/YearsPage';
import { HomeRedirect } from './features/auth/HomeRedirect';
import { LoginPage } from './features/auth/LoginPage';
import { RequireRole } from './features/auth/RequireRole';
import { ADMIN_ROLES } from './features/auth/use-session';
import { FinishPage } from './features/survey/FinishPage';
import { watchSessionGeneration } from './features/survey/page-drafts';
import { SurveyHomePage } from './features/survey/SurveyHomePage';
import { SurveyPageView } from './features/survey/SurveyPageView';
import { NotFoundPage } from './pages/NotFoundPage';

// Las secciones de reglas y simulador se cargan al abrirlas: no pesan en el resto de la app.
const RulesPage = lazy(() =>
  import('./features/admin-rules/RulesPage').then((module) => ({ default: module.RulesPage })),
);
const SimulatorPage = lazy(() =>
  import('./features/admin-simulator/SimulatorPage').then((module) => ({
    default: module.SimulatorPage,
  })),
);

function LoadingSection() {
  return (
    <p role="status" aria-live="polite" className="mt-6 text-ink-muted">
      Cargando…
    </p>
  );
}

export function App() {
  // Si otra pestaña ingresa o cierra sesión, los borradores de esta se descartan de inmediato.
  useEffect(() => watchSessionGeneration(), []);

  return (
    <Routes>
      <Route element={<AppLayout headerActions={<SessionMenu />} />}>
        <Route index element={<HomeRedirect />} />
        <Route path="ingresar" element={<LoginPage />} />
        <Route
          path="encuesta"
          element={
            <RequireRole roles={['ENCUESTADO']}>
              <SurveyHomePage />
            </RequireRole>
          }
        />
        <Route
          path="encuesta/pagina/:pageCode"
          element={
            <RequireRole roles={['ENCUESTADO']}>
              <SurveyPageView />
            </RequireRole>
          }
        />
        <Route
          path="encuesta/terminar"
          element={
            <RequireRole roles={['ENCUESTADO']}>
              <FinishPage />
            </RequireRole>
          }
        />
        <Route
          path="admin"
          element={
            <RequireRole roles={ADMIN_ROLES}>
              <AdminLayout />
            </RequireRole>
          }
        >
          <Route index element={<AdminHomePage />} />
          <Route
            path="cuestionario"
            element={
              <RequireRole roles={['ADMIN_CUESTIONARIO']}>
                <YearsPage />
              </RequireRole>
            }
          />
          <Route
            path="cuestionario/:encuesta/:anio"
            element={
              <RequireRole roles={['ADMIN_CUESTIONARIO']}>
                <QuestionnaireTreePage />
              </RequireRole>
            }
          />
          <Route
            path="cuestionario/:encuesta/:anio/reglas"
            element={
              <RequireRole roles={['ADMIN_CUESTIONARIO']}>
                <Suspense fallback={<LoadingSection />}>
                  <RulesPage />
                </Suspense>
              </RequireRole>
            }
          />
          <Route
            path="cuestionario/:encuesta/:anio/simulador"
            element={
              <RequireRole roles={['ADMIN_CUESTIONARIO']}>
                <Suspense fallback={<LoadingSection />}>
                  <SimulatorPage />
                </Suspense>
              </RequireRole>
            }
          />
          <Route
            path="cuestionario/:encuesta/:anio/publicar"
            element={
              <RequireRole roles={['ADMIN_CUESTIONARIO']}>
                <PublishPage />
              </RequireRole>
            }
          />
          <Route
            path="cuestionario/:encuesta/:anio/historial"
            element={
              <RequireRole roles={['ADMIN_CUESTIONARIO']}>
                <HistoryPage />
              </RequireRole>
            }
          />
          <Route
            path="cuestionario/:encuesta/:anio/paquetes"
            element={
              <RequireRole roles={['ADMIN_CUESTIONARIO']}>
                <PackagesPage />
              </RequireRole>
            }
          />
          <Route
            path="reportes/cobertura"
            element={
              <RequireRole roles={OPERATION_ROLES}>
                <CoveragePage />
              </RequireRole>
            }
          />
          <Route
            path="reportes/cortes"
            element={
              <RequireRole roles={OPERATION_ROLES}>
                <CutsPage />
              </RequireRole>
            }
          />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
