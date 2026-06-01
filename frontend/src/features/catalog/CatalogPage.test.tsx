import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen, userEvent, waitForElementToBeRemoved } from '../../test/test-utils';
import { server } from '../../test/mocks/server';
import { mockEvent, mockSoldOutEvent } from '../../test/mocks/handlers';
import { CatalogPage } from './CatalogPage';

function CatalogRoutes() {
  return (
    <Routes>
      <Route path="/catalogo" element={<CatalogPage />} />
      <Route path="/eventos/:eventoId" element={<h1>Detalle de evento</h1>} />
    </Routes>
  );
}

describe('CatalogPage', () => {
  it('debe mostrar skeleton mientras carga', () => {
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
  });

  it('debe renderizar lista de eventos', async () => {
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(await screen.findByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    expect(screen.getByText(`${mockEvent.cupoDisponible}/${mockEvent.cupoMaximo}`)).toBeInTheDocument();
  });

  it('debe mostrar mensaje vacio si no hay eventos', async () => {
    server.use(http.get('*/api/v1/eventos', () => HttpResponse.json([])));
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    await waitForElementToBeRemoved(() => screen.queryByLabelText('Cargando'));
    expect(screen.getByText(/no hay eventos publicados/i)).toBeInTheDocument();
  });

  it('debe navegar al detalle al seleccionar un evento', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    await screen.findByRole('heading', { name: mockEvent.titulo });
    await user.click(screen.getByRole('button', { name: /ver detalle/i }));

    expect(screen.getByRole('heading', { name: /detalle de evento/i })).toBeInTheDocument();
  });

  it('debe renderizar cupo agotado cuando el backend devuelve cero cupos', async () => {
    server.use(http.get('*/api/v1/eventos', () => HttpResponse.json([mockSoldOutEvent])));
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(await screen.findByRole('heading', { name: mockSoldOutEvent.titulo })).toBeInTheDocument();
    expect(screen.getByText('0/10')).toBeInTheDocument();
  });
});
