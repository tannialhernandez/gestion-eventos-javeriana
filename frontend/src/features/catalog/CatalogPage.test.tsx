import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { seedAuthSession } from '../../test/auth-session';
import { renderWithProviders, screen, userEvent, waitForElementToBeRemoved } from '../../test/test-utils';
import { server } from '../../test/mocks/server';
import { mockEvent, mockOrganizerEvent, mockSoldOutEvent } from '../../test/mocks/handlers';
import { CatalogPage } from './CatalogPage';

function CatalogRoutes() {
  return (
    <Routes>
      <Route path="/catalogo" element={<CatalogPage />} />
      <Route path="/eventos/:eventoId" element={<h1>Detalle de evento</h1>} />
      <Route path="/confirmacion/:inscripcionId" element={<h1>Confirmación</h1>} />
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

  it('debe mostrar banner contextual para participante', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(screen.getByRole('status')).toHaveTextContent('Participante');
    expect(screen.getByRole('status')).toHaveTextContent('Explora eventos académicos y completa tu inscripción');
  });

  it('debe mostrar banner contextual para administrador', async () => {
    seedAuthSession({ role: 'ADMIN' });
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(screen.getByRole('status')).toHaveTextContent('Administrador');
    expect(screen.getByRole('status')).toHaveTextContent('Vista de Administración - Gestión global');
  });

  it('debe ocultar crear y editar eventos para participante', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(await screen.findByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /crear evento/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /editar/i })).not.toBeInTheDocument();
  });

  it('debe mostrar editar solo para evento propio del organizador', async () => {
    server.use(http.get('*/api/v1/eventos', () => HttpResponse.json([mockEvent, mockOrganizerEvent])));
    seedAuthSession({
      role: 'ORGANIZADOR',
      id: mockOrganizerEvent.organizadorId,
      name: 'Carlos Organizador',
    });
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(await screen.findByRole('heading', { name: mockOrganizerEvent.titulo })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /crear evento/i })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /editar/i })).toHaveLength(1);
    expect(screen.getByText('Editable')).toBeInTheDocument();
  });

  it('debe permitir que admin edite todos los eventos y vea badge admin', async () => {
    server.use(http.get('*/api/v1/eventos', () => HttpResponse.json([mockEvent, mockOrganizerEvent])));
    seedAuthSession({
      role: 'ADMIN',
      id: '44444444-4444-4444-4444-444444444444',
      name: 'Ana Administradora',
    });
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(await screen.findByRole('heading', { name: mockOrganizerEvent.titulo })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /crear evento/i })).not.toBeInTheDocument();
    expect(screen.getByLabelText(/filtrar por estado/i)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /editar/i })).toHaveLength(2);
    expect(screen.getAllByText('Editable')).toHaveLength(2);
    expect(screen.getAllByText('Admin')).toHaveLength(2);
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

  it('debe resaltar eventos confirmados y permitir filtrarlos', async () => {
    server.use(
      http.get('*/api/v1/eventos', () => HttpResponse.json([mockEvent, mockOrganizerEvent])),
      http.get('*/api/v1/inscripciones/mias', () => HttpResponse.json([
        {
          inscripcionId: 'insc-confirmada',
          eventoId: mockEvent.id,
          tarifaId: 'tarifa-001',
          estado: 'CONFIRMADA',
          fechaInscripcion: '2026-05-31T12:00:00Z',
          fechaExpiracionPago: null,
          checkoutUrl: null,
          expiraEnSegundos: 0,
        },
      ])),
    );
    seedAuthSession({ role: 'PARTICIPANTE' });
    const user = userEvent.setup();
    renderWithProviders(<CatalogRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(await screen.findByText('Inscrito')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: mockOrganizerEvent.titulo })).toBeInTheDocument();

    await user.click(screen.getByLabelText(/mis confirmados/i));

    expect(screen.getByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: mockOrganizerEvent.titulo })).not.toBeInTheDocument();
  });
});
