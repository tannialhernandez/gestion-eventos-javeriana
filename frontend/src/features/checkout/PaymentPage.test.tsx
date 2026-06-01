import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen, userEvent, waitFor } from '../../test/test-utils';
import { server } from '../../test/mocks/server';
import { saveCheckoutSnapshot } from './storage';
import { PaymentPage } from './PaymentPage';

function PaymentRoutes() {
  return (
    <Routes>
      <Route path="/inscripciones/:inscripcionId/pago" element={<PaymentPage />} />
      <Route path="/confirmacion/:inscripcionId" element={<h1>Confirmacion</h1>} />
      <Route path="/catalogo" element={<h1>Catalogo</h1>} />
    </Routes>
  );
}

function seedCheckout() {
  saveCheckoutSnapshot('insc-test-uuid', {
    eventTitle: 'Congreso de Arquitectura 2026',
    eventId: 'evento-001',
    tariffId: 'tarifa-001',
    amount: 150000,
    currency: 'COP',
    checkoutUrl: 'https://wiremock.local/checkout',
    expiresAt: '2033-05-31T12:00:00Z',
  });
}

describe('PaymentPage', () => {
  it('debe mostrar resumen y confirmar pago aprobado', async () => {
    seedCheckout();
    const user = userEvent.setup();
    renderWithProviders(<PaymentRoutes />, { routerProps: { initialEntries: ['/inscripciones/insc-test-uuid/pago'] } });

    expect(screen.getByRole('heading', { name: /congreso de arquitectura 2026/i })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /confirmar pago aprobado/i }));

    await waitFor(() => expect(screen.getByRole('heading', { name: /confirmacion/i })).toBeInTheDocument());
  });

  it('debe mostrar error contextual si falla la confirmacion', async () => {
    seedCheckout();
    server.use(http.post('*/api/v1/webhooks/pagos', () => HttpResponse.json({ message: 'Pago rechazado' }, { status: 500 })));
    const user = userEvent.setup();
    renderWithProviders(<PaymentRoutes />, { routerProps: { initialEntries: ['/inscripciones/insc-test-uuid/pago'] } });

    await user.click(screen.getByRole('button', { name: /confirmar pago aprobado/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/pago rechazado|solicitud/i);
    expect(screen.getByRole('button', { name: /reintentar/i })).toBeInTheDocument();
  });

  it('debe ofrecer volver al catalogo si no hay checkout activo', () => {
    renderWithProviders(<PaymentRoutes />, { routerProps: { initialEntries: ['/inscripciones/insc-test-uuid/pago'] } });

    expect(screen.getByText(/no hay una inscripción activa/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /volver al catálogo/i })).toBeInTheDocument();
  });
});
