import { http, HttpResponse } from 'msw';
import type { AcademicEvent, Tariff } from '../../entities/event';
import type { AttendanceRecord, Inscription } from '../../entities/inscription';

export const mockJwt =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLWRlbW8tMDAxIiwiZW1haWwiOiJkaWVnby5wYXJ0aWNpcGFudGVAamF2ZXJpYW5hLmVkdS5jbyIsIm5hbWUiOiJEaWVnbyBQYXJ0aWNpcGFudGUiLCJyb2xlcyI6WyJQQVJUSUNJUEFOVEUiXSwiZXhwIjoxOTk5OTk5OTk5fQ.mock-signature';

export const mockEvent: AcademicEvent = {
  id: '00000000-0000-0000-0000-000000000001',
  titulo: 'Congreso de Arquitectura 2026',
  descripcion: 'Encuentro academico sobre arquitectura de software.',
  tipo: 'CONGRESO',
  modalidad: 'HIBRIDO',
  fechaInicio: '2026-08-15T09:00:00Z',
  fechaFin: '2026-08-16T17:00:00Z',
  fechaLimiteInscripcion: '2026-08-01T23:59:59Z',
  cupoMaximo: 250,
  cupoDisponible: 120,
  estado: 'PUBLICADO',
  organizadorId: 'organizador-001',
  aceptaInscripciones: true,
};

export const mockOrganizerEvent: AcademicEvent = {
  ...mockEvent,
  id: '00000000-0000-0000-0000-000000000777',
  titulo: 'Evento propio del organizador',
  organizadorId: '33333333-3333-3333-3333-333333333333',
};

export const mockSoldOutEvent: AcademicEvent = {
  ...mockEvent,
  id: '00000000-0000-0000-0000-000000000099',
  titulo: 'Taller sin cupos',
  cupoMaximo: 10,
  cupoDisponible: 0,
};

export const mockTariff: Tariff = {
  id: '00000000-0000-0000-0001-000000000001',
  descripcion: 'Tarifa general',
  monto: 150000,
  moneda: 'COP',
};

export const mockInscription: Inscription = {
  inscripcionId: 'insc-test-uuid',
  eventoId: mockEvent.id,
  estado: 'PENDIENTE_PAGO',
  fechaInscripcion: '2026-05-31T12:00:00Z',
  fechaExpiracionPago: '2026-05-31T12:15:00Z',
  checkoutUrl: 'https://wiremock.local/checkout',
  expiraEnSegundos: 900,
};

export const mockAttendance: AttendanceRecord = {
  inscripcionId: 'insc-test-uuid',
  eventoId: mockEvent.id,
  usuarioId: 'user-demo-001',
  participante: 'Participante user-dem',
  estado: 'CONFIRMADA',
  asistio: false,
  fechaRegistro: null,
  registradoPor: null,
  observaciones: null,
};

export const handlers = [
  http.post('*/api/v1/auth/login', async ({ request }) => {
    const body = await request.json() as { email?: string; password?: string };
    if (body.email === 'diego.participante@javeriana.edu.co' && body.password === 'demo123') {
      return HttpResponse.json({
        accessToken: mockJwt,
        tokenType: 'Bearer',
        expiresAt: '2033-05-31T12:00:00Z',
        user: {
          id: 'user-demo-001',
          nombre: 'Diego Participante',
          email: body.email,
          roles: ['PARTICIPANTE'],
        },
      });
    }

    return HttpResponse.json({ message: 'Credenciales invalidas' }, { status: 401 });
  }),

  http.get('*/api/v1/eventos', () => HttpResponse.json([mockEvent])),

  http.get('*/api/v1/eventos/:eventoId', ({ params }) => {
    const event = params.eventoId === mockSoldOutEvent.id
      ? mockSoldOutEvent
      : params.eventoId === mockOrganizerEvent.id
        ? mockOrganizerEvent
        : mockEvent;
    return HttpResponse.json(event);
  }),

  http.get('*/api/v1/tarifas', () => HttpResponse.json([mockTariff])),

  http.post('*/api/v1/tarifas', async ({ request }) => {
    const body = await request.json() as Partial<Tariff> & { eventoId?: string };
    return HttpResponse.json({
      ...mockTariff,
      ...body,
      id: '00000000-0000-0000-0001-000000000888',
    }, { status: 201 });
  }),

  http.put('*/api/v1/tarifas/:tarifaId', async ({ request, params }) => {
    const body = await request.json() as Partial<Tariff>;
    return HttpResponse.json({
      ...mockTariff,
      ...body,
      id: String(params.tarifaId),
    });
  }),

  http.get('*/api/v1/inscripciones/mia', () => new HttpResponse(null, { status: 404 })),

  http.get('*/api/v1/inscripciones/mias', () => HttpResponse.json([])),

  http.post('*/api/v1/inscripciones', () => HttpResponse.json(mockInscription, { status: 201 })),

  http.post('*/api/v1/inscripciones/:inscripcionId/cancelar', ({ params }) => HttpResponse.json({
    ...mockInscription,
    inscripcionId: String(params.inscripcionId),
    estado: 'CANCELADA',
    checkoutUrl: null,
    fechaExpiracionPago: null,
    expiraEnSegundos: 0,
  })),

  http.get('*/api/v1/asistencias/eventos/:eventoId', ({ params }) => HttpResponse.json([{
    ...mockAttendance,
    eventoId: String(params.eventoId),
  }])),

  http.post('*/api/v1/asistencias/eventos/:eventoId', async ({ request, params }) => {
    const body = await request.json() as { inscripcionId: string; asistio: boolean };
    return HttpResponse.json({
      ...mockAttendance,
      eventoId: String(params.eventoId),
      inscripcionId: body.inscripcionId,
      asistio: body.asistio,
      fechaRegistro: '2026-06-03T10:00:00Z',
      registradoPor: 'organizador@javeriana.edu.co',
    });
  }),

  http.get('*/api/v1/asistencias/mia', () => HttpResponse.json(mockAttendance)),

  http.get('*/api/v1/certificados/:inscripcionId', () => new HttpResponse(new Blob(['%PDF-1.4 certificado'], {
    type: 'application/pdf',
  }))),

  http.post('*/api/v1/pagos/simulador/:inscripcionId/aprobar', () => HttpResponse.json({ resultado: 'CONFIRMADO' })),

  http.post('*/api/v1/eventos', async ({ request }) => {
    const body = await request.json() as Partial<AcademicEvent>;
    return HttpResponse.json({
      ...mockOrganizerEvent,
      ...body,
      id: '00000000-0000-0000-0000-000000000888',
      cupoDisponible: body.cupoMaximo ?? mockOrganizerEvent.cupoDisponible,
      estado: 'BORRADOR',
      aceptaInscripciones: false,
    }, { status: 201 });
  }),

  http.post('*/api/v1/eventos/:eventoId/publicar', () => new HttpResponse(null, { status: 204 })),

  http.post('*/api/v1/eventos/:eventoId/enviar-revision', ({ params }) => HttpResponse.json({
    ...mockOrganizerEvent,
    id: String(params.eventoId),
    estado: 'PENDIENTE_PUBLICACION',
    aceptaInscripciones: false,
  })),

  http.post('*/api/v1/eventos/:eventoId/aprobar', ({ params }) => HttpResponse.json({
    ...mockOrganizerEvent,
    id: String(params.eventoId),
    estado: 'PUBLICADO',
    aceptaInscripciones: true,
  })),

  http.post('*/api/v1/eventos/:eventoId/rechazar', ({ params }) => HttpResponse.json({
    ...mockOrganizerEvent,
    id: String(params.eventoId),
    estado: 'RECHAZADO',
    aceptaInscripciones: false,
  })),

  http.put('*/api/v1/eventos/:eventoId', async ({ request, params }) => {
    const body = await request.json() as Partial<AcademicEvent>;
    return HttpResponse.json({
      ...mockOrganizerEvent,
      ...body,
      id: String(params.eventoId),
      cupoDisponible: body.cupoMaximo ?? mockOrganizerEvent.cupoDisponible,
    });
  }),

  http.delete('*/api/v1/eventos/:eventoId', () => new HttpResponse(null, { status: 204 })),

  http.post('*/api/v1/eventos/:eventoId/cancelar', () => new HttpResponse(null, { status: 204 })),
];
