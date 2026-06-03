import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from '../features/auth';
import { CatalogPage } from '../features/catalog';
import { EventDetailPage } from '../features/events';
import { EventCreatePage, EventEditPage } from '../features/event-management';
import { PaymentPage, ConfirmationPage } from '../features/checkout';
import { AppShell } from './AppShell';
import { RequireAuth } from './RequireAuth';
import { RequireRole } from './RequireRole';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<Navigate to="/catalogo" replace />} />
          <Route path="/catalogo" element={<CatalogPage />} />
          <Route
            path="/eventos/nuevo"
            element={(
              <RequireRole allowedRoles={['ORGANIZADOR']}>
                <EventCreatePage />
              </RequireRole>
            )}
          />
          <Route path="/eventos/:eventoId" element={<EventDetailPage />} />
          <Route
            path="/eventos/:eventoId/editar"
            element={(
              <RequireRole allowedRoles={['ADMIN', 'ORGANIZADOR']}>
                <EventEditPage />
              </RequireRole>
            )}
          />
          <Route path="/inscripciones/:inscripcionId/pago" element={<PaymentPage />} />
          <Route path="/confirmacion/:inscripcionId" element={<ConfirmationPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/catalogo" replace />} />
    </Routes>
  );
}
