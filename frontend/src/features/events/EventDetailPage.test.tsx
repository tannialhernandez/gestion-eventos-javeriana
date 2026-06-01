import { delay, http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen, userEvent, waitFor } from '../../test/test-utils';
import { server } from '../../test/mocks/server';
import { mockEvent, mockInscription, mockSoldOutEvent } from '../../test/mocks/handlers';
import { EventDetailPage } from './EventDetailPage';

function DetailRoutes() {
  return (
    <Routes>
      <Route path="/eventos/:eventoId" element={<EventDetailPage />} />
      <Route path="/inscripciones/:inscripcionId/pago" element={<h1>Pantalla de pago</h1>} />
      <Route path="/catalogo" element={<h1>Catalogo</h1>} />
    </Routes>
  );
}

describe('EventDetailPage', () => {
  it('debe cargar evento, tarifas y permitir crear inscripcion', async () => {
    const user = userEvent.setup();
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /inscribirme y pagar/i }));

    await waitFor(() => expect(screen.getByRole('heading', { name: /pantalla de pago/i })).toBeInTheDocument());
  });

  it('debe deshabilitar submit mientras procesa', async () => {
    server.use(
      http.post('*/api/v1/inscripciones', async () => {
        await delay(150);
        return HttpResponse.json(mockInscription, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /inscribirme y pagar/i }));

    expect(screen.getByRole('button', { name: /reservando cupo/i })).toBeDisabled();
  });

  it('debe mostrar cupo agotado cuando inscription-service responde 409', async () => {
    server.use(http.post('*/api/v1/inscripciones', () => HttpResponse.json({ message: 'Sin cupos' }, { status: 409 })));
    const user = userEvent.setup();
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /inscribirme y pagar/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/cupo agotado/i);
  });

  it('debe deshabilitar inscripcion si el evento no tiene cupos', async () => {
    renderWithProviders(<DetailRoutes />, {
      routerProps: { initialEntries: [`/eventos/${mockSoldOutEvent.id}`] },
    });

    expect(await screen.findByRole('heading', { name: mockSoldOutEvent.titulo })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /inscribirme y pagar/i })).toBeDisabled();
  });
});
