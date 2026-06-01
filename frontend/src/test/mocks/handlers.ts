import { http, HttpResponse } from 'msw';
import type { AcademicEvent, Tariff } from '../../entities/event';
import type { Inscription } from '../../entities/inscription';

export const mockJwt =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLWRlbW8tMDAxIiwiZW1haWwiOiJkaWVnby5wYXJ0aWNpcGFudGVAamF2ZXJpYW5hLmVkdS5jbyIsIm5hbWUiOiJEaWVnbyBQYXJ0aWNpcGFudGUiLCJyb2xlcyI6WyJQQVJUSUNJUEFOVEUiXSwiZXhwIjoxOTk5OTk5OTk5fQ.mock-signature';

export const mockEvent: AcademicEvent = {
  id: '00000000-0000-0000-0000-000000000001',
  titulo: 'Congreso de Arquitectura 2026',
  descripcion: 'Encuentro academico sobre arquitectura de software.',
  tipo: 'CONGRESO',
  modalidad: 'HIBRIDA',
  fechaInicio: '2026-08-15T09:00:00Z',
  fechaFin: '2026-08-16T17:00:00Z',
  fechaLimiteInscripcion: '2026-08-01T23:59:59Z',
  cupoMaximo: 250,
  cupoDisponible: 120,
  estado: 'PUBLICADO',
  organizadorId: 'organizador-001',
  aceptaInscripciones: true,
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
    const event = params.eventoId === mockSoldOutEvent.id ? mockSoldOutEvent : mockEvent;
    return HttpResponse.json(event);
  }),

  http.get('*/api/v1/tarifas', () => HttpResponse.json([mockTariff])),

  http.post('*/api/v1/inscripciones', () => HttpResponse.json(mockInscription, { status: 201 })),

  http.post('*/api/v1/webhooks/pagos', () => HttpResponse.json({ resultado: 'CONFIRMADO' })),
];
