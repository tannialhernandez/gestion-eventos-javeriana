import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { seedAuthSession } from '../../test/auth-session';
import { mockOrganizerEvent } from '../../test/mocks/handlers';
import { server } from '../../test/mocks/server';
import { renderWithProviders, screen, userEvent, waitFor } from '../../test/test-utils';
import { EventEditPage } from './EventEditPage';

function EditRoutes() {
  return (
    <Routes>
      <Route path="/eventos/:eventoId/editar" element={<EventEditPage />} />
      <Route path="/eventos/:eventoId" element={<h1>Detalle actualizado</h1>} />
      <Route path="/catalogo" element={<h1>Catálogo</h1>} />
    </Routes>
  );
}

describe('EventEditPage', () => {
  it('carga datos existentes y guarda cambios', async () => {
    seedAuthSession({
      role: 'ORGANIZADOR',
      id: mockOrganizerEvent.organizadorId,
      name: 'Carlos Organizador',
    });
    const user = userEvent.setup();
    renderWithProviders(<EditRoutes />, { routerProps: { initialEntries: [`/eventos/${mockOrganizerEvent.id}/editar`] } });

    const title = await screen.findByLabelText(/título/i);
    expect(title).toHaveValue(mockOrganizerEvent.titulo);
    await user.clear(title);
    await user.type(title, 'Evento editado por organizador');
    await user.click(screen.getByRole('button', { name: /guardar cambios/i }));

    await waitFor(() => expect(screen.getByRole('heading', { name: /detalle actualizado/i })).toBeInTheDocument());
  });

  it('elimina evento tras confirmacion y vuelve al catalogo', async () => {
    seedAuthSession({
      role: 'ORGANIZADOR',
      id: mockOrganizerEvent.organizadorId,
      name: 'Carlos Organizador',
    });
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderWithProviders(<EditRoutes />, { routerProps: { initialEntries: [`/eventos/${mockOrganizerEvent.id}/editar`] } });

    expect(await screen.findByRole('heading', { name: /editar evento/i })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /eliminar/i }));

    await waitFor(() => expect(screen.getByRole('heading', { name: /catálogo/i })).toBeInTheDocument());
    confirm.mockRestore();
  });

  it('muestra error correcto cuando la API productiva rechaza PUT con 401', async () => {
    server.use(http.put('*/api/v1/eventos/:eventoId', () => new HttpResponse(null, { status: 401 })));
    seedAuthSession({
      role: 'ORGANIZADOR',
      id: mockOrganizerEvent.organizadorId,
      name: 'Carlos Organizador',
    });
    const user = userEvent.setup();
    renderWithProviders(<EditRoutes />, { routerProps: { initialEntries: [`/eventos/${mockOrganizerEvent.id}/editar`] } });

    const title = await screen.findByLabelText(/título/i);
    await user.clear(title);
    await user.type(title, 'Evento con PUT rechazado');
    await user.click(screen.getByRole('button', { name: /guardar cambios/i }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/api productiva rechazó la edición/i);
    expect(alert).not.toHaveTextContent(/sesión expirada/i);
  });
});
