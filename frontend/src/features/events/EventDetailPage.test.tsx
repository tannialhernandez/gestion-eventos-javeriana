import { delay, http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { seedAuthSession } from '../../test/auth-session';
import { renderWithProviders, screen, userEvent, waitFor } from '../../test/test-utils';
import { server } from '../../test/mocks/server';
import { mockAttendance, mockEvent, mockInscription, mockOrganizerEvent, mockSoldOutEvent } from '../../test/mocks/handlers';
import { EventDetailPage } from './EventDetailPage';

function DetailRoutes() {
  return (
    <Routes>
      <Route path="/eventos/:eventoId" element={<EventDetailPage />} />
      <Route path="/eventos/:eventoId/editar" element={<h1>Editar evento</h1>} />
      <Route path="/inscripciones/:inscripcionId/pago" element={<h1>Pantalla de pago</h1>} />
      <Route path="/confirmacion/:inscripcionId" element={<h1>Inscripción confirmada</h1>} />
      <Route path="/catalogo" element={<h1>Catalogo</h1>} />
    </Routes>
  );
}

describe('EventDetailPage', () => {
  it('debe cargar evento, tarifas y permitir crear inscripcion', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    const user = userEvent.setup();
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^inscribirme$/i }));

    await waitFor(() => expect(screen.getByRole('heading', { name: /pantalla de pago/i })).toBeInTheDocument());
  });

  it('debe ir a confirmacion si la inscripcion ya esta confirmada', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    server.use(
      http.post('*/api/v1/inscripciones', () => HttpResponse.json({
        ...mockInscription,
        estado: 'CONFIRMADA',
        checkoutUrl: null,
        fechaExpiracionPago: null,
        expiraEnSegundos: 0,
      }, { status: 200 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^inscribirme$/i }));

    await waitFor(() => expect(screen.getByRole('heading', { name: /inscripción confirmada/i })).toBeInTheDocument());
  });

  it('debe mostrar estado confirmado si el participante ya esta inscrito', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    server.use(
      http.get('*/api/v1/inscripciones/mia', () => HttpResponse.json({
        ...mockInscription,
        estado: 'CONFIRMADA',
        checkoutUrl: null,
        fechaExpiracionPago: null,
        expiraEnSegundos: 0,
      })),
    );
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByText(/ya estás inscrito/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /ver confirmación/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^inscribirme$/i })).not.toBeInTheDocument();
  });

  it('debe mostrar descarga de certificado cuando la asistencia esta confirmada', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    server.use(
      http.get('*/api/v1/inscripciones/mia', () => HttpResponse.json({
        ...mockInscription,
        estado: 'CONFIRMADA',
        checkoutUrl: null,
        fechaExpiracionPago: null,
        expiraEnSegundos: 0,
      })),
      http.get('*/api/v1/asistencias/mia', () => HttpResponse.json({
        ...mockAttendance,
        asistio: true,
      })),
    );
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByRole('button', { name: /descargar certificado/i })).toBeInTheDocument();
  });

  it('debe mostrar continuar pago si el participante tiene inscripcion pendiente', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    server.use(
      http.get('*/api/v1/inscripciones/mia', () => HttpResponse.json(mockInscription)),
    );
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByText(/tienes una inscripción pendiente/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /continuar pago/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^inscribirme$/i })).not.toBeInTheDocument();
  });

  it('debe permitir darse de baja y volver a mostrar inscripcion disponible', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    let canceled = false;
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    server.use(
      http.get('*/api/v1/inscripciones/mia', () => canceled
        ? new HttpResponse(null, { status: 404 })
        : HttpResponse.json({
          ...mockInscription,
          estado: 'CONFIRMADA',
          checkoutUrl: null,
          fechaExpiracionPago: null,
          expiraEnSegundos: 0,
        })),
      http.post('*/api/v1/inscripciones/:inscripcionId/cancelar', ({ params }) => {
        canceled = true;
        return HttpResponse.json({
          ...mockInscription,
          inscripcionId: String(params.inscripcionId),
          estado: 'CANCELADA',
          checkoutUrl: null,
          fechaExpiracionPago: null,
          expiraEnSegundos: 0,
        });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByText(/ya estás inscrito/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /darme de baja/i }));

    await waitFor(() => expect(screen.getByRole('button', { name: /^inscribirme$/i })).toBeInTheDocument());
  });

  it('debe deshabilitar submit mientras procesa', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    server.use(
      http.post('*/api/v1/inscripciones', async () => {
        await delay(150);
        return HttpResponse.json(mockInscription, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^inscribirme$/i }));

    expect(screen.getByRole('button', { name: /reservando cupo/i })).toBeDisabled();
  });

  it('debe mostrar cupo agotado cuando inscription-service responde 409', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    server.use(http.post('*/api/v1/inscripciones', () => HttpResponse.json({ message: 'Sin cupos' }, { status: 409 })));
    const user = userEvent.setup();
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^inscribirme$/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/cupo agotado/i);
  });

  it('muestra error de inscripcion cuando inscription-service rechaza con 401', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    server.use(http.post('*/api/v1/inscripciones', () => new HttpResponse(null, { status: 401 })));
    const user = userEvent.setup();
    renderWithProviders(<DetailRoutes />, { routerProps: { initialEntries: [`/eventos/${mockEvent.id}`] } });

    expect(await screen.findByRole('heading', { name: mockEvent.titulo })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^inscribirme$/i }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/api productiva rechazó la inscripción/i);
    expect(alert).not.toHaveTextContent(/sesión expirada/i);
  });

  it('debe deshabilitar inscripcion si el evento no tiene cupos', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    renderWithProviders(<DetailRoutes />, {
      routerProps: { initialEntries: [`/eventos/${mockSoldOutEvent.id}`] },
    });

    expect(await screen.findByRole('heading', { name: mockSoldOutEvent.titulo })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^inscribirme$/i })).toBeDisabled();
  });

  it('debe mostrar acciones de gestion para evento propio del organizador y ocultar inscripcion', async () => {
    seedAuthSession({
      role: 'ORGANIZADOR',
      id: mockOrganizerEvent.organizadorId,
      name: 'Carlos Organizador',
    });
    renderWithProviders(<DetailRoutes />, {
      routerProps: { initialEntries: [`/eventos/${mockOrganizerEvent.id}`] },
    });

    expect(await screen.findByRole('heading', { name: mockOrganizerEvent.titulo })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^editar$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancelar evento/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /inscribirme/i })).not.toBeInTheDocument();
  });

  it('debe permitir al organizador marcar asistencia', async () => {
    seedAuthSession({
      role: 'ORGANIZADOR',
      id: mockOrganizerEvent.organizadorId,
      name: 'Carlos Organizador',
    });
    const user = userEvent.setup();
    server.use(
      http.get('*/api/v1/asistencias/eventos/:eventoId', () => HttpResponse.json([{
        ...mockAttendance,
        eventoId: mockOrganizerEvent.id,
        asistio: false,
      }])),
    );

    renderWithProviders(<DetailRoutes />, {
      routerProps: { initialEntries: [`/eventos/${mockOrganizerEvent.id}`] },
    });

    expect(await screen.findByRole('heading', { name: /registro de asistencia/i })).toBeInTheDocument();
    const attendanceCheckbox = await screen.findByRole('checkbox', { name: /marcar asistencia/i });
    await user.click(attendanceCheckbox);

    await waitFor(() => expect(attendanceCheckbox).toBeChecked());
  });

  it('debe permitir que admin gestione evento de cualquier organizador', async () => {
    seedAuthSession({
      role: 'ADMIN',
      id: '44444444-4444-4444-4444-444444444444',
      name: 'Ana Administradora',
    });
    renderWithProviders(<DetailRoutes />, {
      routerProps: { initialEntries: [`/eventos/${mockOrganizerEvent.id}`] },
    });

    expect(await screen.findByRole('heading', { name: mockOrganizerEvent.titulo })).toBeInTheDocument();
    expect(screen.getByText('Admin')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^editar$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancelar evento/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /inscribirme/i })).not.toBeInTheDocument();
  });
});
