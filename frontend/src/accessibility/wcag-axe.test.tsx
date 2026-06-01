import { axe } from 'vitest-axe';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ContextualError, DegradedServiceBanner } from '../components';
import { ServiceUnavailableError } from '../lib/errors';
import { renderWithProviders, screen } from '../test/test-utils';
import { mockEvent, mockInscription, mockTariff } from '../test/mocks/handlers';
import { LoginPage } from '../features/auth/LoginPage';
import { CatalogPage } from '../features/catalog/CatalogPage';
import { EventDetailPage } from '../features/events/EventDetailPage';
import { PaymentPage } from '../features/checkout/PaymentPage';
import { saveCheckoutSnapshot } from '../features/checkout/storage';

async function expectNoViolations(container: HTMLElement) {
  const results = await axe(container);
  expect(results).toHaveNoViolations();
}

describe('WCAG 2.1 AA con axe-core', () => {
  it('no reporta violaciones en el formulario de login', async () => {
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/catalogo" element={<h1>Catálogo protegido</h1>} />
      </Routes>,
      { routerProps: { initialEntries: ['/login'] } },
    );

    const form = container.querySelector('.login-form');
    expect(form).not.toBeNull();
    await expectNoViolations(form as HTMLElement);
  });

  it('no reporta violaciones en el catálogo con filtros', async () => {
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/catalogo" element={<main><CatalogPage /></main>} />
      </Routes>,
      { routerProps: { initialEntries: ['/catalogo'] } },
    );

    await screen.findByRole('heading', { name: mockEvent.titulo });
    await expectNoViolations(container);
  });

  it('no reporta violaciones en el detalle de evento e inscripción', async () => {
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/eventos/:eventoId" element={<main><EventDetailPage /></main>} />
        <Route path="/inscripciones/:inscripcionId/pago" element={<h1>Pago</h1>} />
        <Route path="/catalogo" element={<h1>Catálogo</h1>} />
      </Routes>,
      { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } },
    );

    await screen.findByRole('heading', { name: mockEvent.titulo });
    await expectNoViolations(container);
  });

  it('no reporta violaciones en pago y enlaces externos', async () => {
    saveCheckoutSnapshot(mockInscription.inscripcionId, {
      eventTitle: mockEvent.titulo,
      eventId: mockEvent.id,
      tariffId: mockTariff.id,
      amount: mockTariff.monto,
      currency: mockTariff.moneda,
      checkoutUrl: mockInscription.checkoutUrl,
      expiresAt: mockInscription.fechaExpiracionPago,
    });

    const { container } = renderWithProviders(
      <Routes>
        <Route path="/inscripciones/:inscripcionId/pago" element={<main><PaymentPage /></main>} />
        <Route path="/confirmacion/:inscripcionId" element={<h1>Confirmación</h1>} />
        <Route path="/catalogo" element={<h1>Catálogo</h1>} />
      </Routes>,
      { routerProps: { initialEntries: [`/inscripciones/${mockInscription.inscripcionId}/pago`] } },
    );

    await screen.findByRole('heading', { name: /resumen/i });
    await expectNoViolations(container);
  });

  it('no reporta violaciones en mensajes de degradación controlada', async () => {
    const { container } = renderWithProviders(
      <main>
        <ContextualError
          error={new ServiceUnavailableError('Servicio temporalmente no disponible', {
            retryAfterSeconds: 30,
            serviceName: 'event-service',
            status: 503,
          })}
        />
        <DegradedServiceBanner />
      </main>,
    );

    await expectNoViolations(container);
  });
});
