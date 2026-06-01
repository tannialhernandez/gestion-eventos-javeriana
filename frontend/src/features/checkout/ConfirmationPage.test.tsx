import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen } from '../../test/test-utils';
import { saveCheckoutSnapshot } from './storage';
import { ConfirmationPage } from './ConfirmationPage';

function ConfirmationRoutes() {
  return (
    <Routes>
      <Route path="/confirmacion/:inscripcionId" element={<ConfirmationPage />} />
      <Route path="/catalogo" element={<h1>Catalogo</h1>} />
    </Routes>
  );
}

describe('ConfirmationPage', () => {
  it('debe mostrar confirmacion con snapshot de pago', () => {
    saveCheckoutSnapshot('insc-test-uuid', {
      eventTitle: 'Congreso de Arquitectura 2026',
      eventId: 'evento-001',
      tariffId: 'tarifa-001',
      amount: 150000,
      currency: 'COP',
      checkoutUrl: null,
      expiresAt: '2033-05-31T12:00:00Z',
    });

    renderWithProviders(<ConfirmationRoutes />, { routerProps: { initialEntries: ['/confirmacion/insc-test-uuid'] } });

    expect(screen.getByRole('heading', { name: /inscripción en proceso de confirmación/i })).toBeInTheDocument();
    expect(screen.getByText(/congreso de arquitectura 2026/i)).toBeInTheDocument();
    expect(screen.getByText('insc-test-uuid')).toBeInTheDocument();
  });

  it('debe mostrar confirmacion aunque no exista snapshot local', () => {
    renderWithProviders(<ConfirmationRoutes />, { routerProps: { initialEntries: ['/confirmacion/insc-test-uuid'] } });

    expect(screen.getByText(/el pago fue notificado al payment-service/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /volver al catálogo/i })).toBeInTheDocument();
  });
});
