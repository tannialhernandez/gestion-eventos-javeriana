import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from '../features/auth';
import { CatalogPage } from '../features/catalog';
import { EventDetailPage } from '../features/events';
import { PaymentPage, ConfirmationPage } from '../features/checkout';
import { AppShell } from './AppShell';
import { RequireAuth } from './RequireAuth';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<Navigate to="/catalogo" replace />} />
          <Route path="/catalogo" element={<CatalogPage />} />
          <Route path="/eventos/:eventoId" element={<EventDetailPage />} />
          <Route path="/inscripciones/:inscripcionId/pago" element={<PaymentPage />} />
          <Route path="/confirmacion/:inscripcionId" element={<ConfirmationPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/catalogo" replace />} />
    </Routes>
  );
}
