import { Route, Routes } from 'react-router';
import { AppLayout } from './components/AppLayout';
import { SessionMenu } from './components/SessionMenu';
import { AdminHomePage } from './features/admin/AdminHomePage';
import { HomeRedirect } from './features/auth/HomeRedirect';
import { LoginPage } from './features/auth/LoginPage';
import { RequireRole } from './features/auth/RequireRole';
import { ADMIN_ROLES } from './features/auth/use-session';
import { FinishPage } from './features/survey/FinishPage';
import { SurveyHomePage } from './features/survey/SurveyHomePage';
import { SurveyPageView } from './features/survey/SurveyPageView';
import { NotFoundPage } from './pages/NotFoundPage';

export function App() {
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
              <AdminHomePage />
            </RequireRole>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
