import { mkdirSync } from 'node:fs';
import type { Page, Route } from '@playwright/test';

export const evidenceDir = 'test-results/evidence';
export const authEvidenceDir = `${evidenceDir}/auth`;

type DemoRole = 'PARTICIPANTE' | 'ORGANIZADOR' | 'ADMIN';

export type DemoUser = {
  id: string;
  name: string;
  email: string;
  password: string;
  roles: DemoRole[];
  slug: string;
};

export const demoUsers: DemoUser[] = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Laura Participante',
    email: 'laura.participante@javeriana.edu.co',
    password: 'demo123',
    roles: ['PARTICIPANTE'],
    slug: 'laura-participante',
  },
  {
    id: '22222222-2222-2222-2222-222222222222',
    name: 'Diego Participante',
    email: 'diego.participante@javeriana.edu.co',
    password: 'demo123',
    roles: ['PARTICIPANTE'],
    slug: 'diego-participante',
  },
  {
    id: '33333333-3333-3333-3333-333333333333',
    name: 'Carlos Organizador',
    email: 'carlos.organizador@javeriana.edu.co',
    password: 'demo123',
    roles: ['ORGANIZADOR'],
    slug: 'carlos-organizador',
  },
  {
    id: '44444444-4444-4444-4444-444444444444',
    name: 'Ana Administradora',
    email: 'ana.admin@javeriana.edu.co',
    password: 'demo123',
    roles: ['ADMIN'],
    slug: 'ana-admin',
  },
  {
    id: '55555555-5555-5555-5555-555555555555',
    name: 'Sofia Mesa de Ayuda',
    email: 'sofia.soporte@javeriana.edu.co',
    password: 'demo123',
    roles: ['PARTICIPANTE'],
    slug: 'sofia-soporte',
  },
];

function base64UrlJson(value: unknown) {
  return Buffer.from(JSON.stringify(value))
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

export function createMockJwt(user: DemoUser = demoUsers[1], exp = 1_999_999_999) {
  const header = base64UrlJson({ alg: 'RS256', typ: 'JWT', kid: 'local-demo-rsa' });
  const payload = base64UrlJson({
    sub: user.id,
    email: user.email,
    name: user.name,
    roles: user.roles,
    exp,
  });
  return `${header}.${payload}.mock-signature`;
}

export const mockJwt = createMockJwt();

export const eventId = '00000000-0000-0000-0000-000000000001';
export const organizerEventId = '00000000-0000-0000-0000-000000000777';
export const tariffId = '00000000-0000-0000-0001-000000000001';
export const inscriptionId = 'insc-e2e-uuid';

const event = {
  id: eventId,
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

const organizerEvent = {
  ...event,
  id: organizerEventId,
  titulo: 'Seminario propio de Carlos',
  descripcion: 'Evento administrado por el organizador demo.',
  organizadorId: '33333333-3333-3333-3333-333333333333',
};

const tariff = {
  id: tariffId,
  descripcion: 'Tarifa general',
  monto: 150000,
  moneda: 'COP',
};

export async function prepareEvidenceDir() {
  mkdirSync(evidenceDir, { recursive: true });
  mkdirSync(authEvidenceDir, { recursive: true });
}

async function json(route: Route, body: unknown, status = 200, headers: Record<string, string> = {}) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    headers,
    body: JSON.stringify(body),
  });
}

export async function mockBackend(page: Page) {
  const events = [event, organizerEvent].map((item) => ({ ...item }));
  let createdSequence = 900;

  await page.route('**/auth-api/api/v1/auth/login', async (route) => {
    const body = route.request().postDataJSON() as { email?: string; password?: string };
    const user = demoUsers.find((candidate) => candidate.email === body.email && candidate.password === body.password);

    if (user) {
      await json(route, {
        accessToken: createMockJwt(user),
        tokenType: 'Bearer',
        expiresAt: '2033-05-31T12:00:00Z',
        user: {
          id: user.id,
          nombre: user.name,
          email: user.email,
          roles: user.roles,
        },
      });
      return;
    }
    await json(route, { message: 'Credenciales invalidas' }, 401);
  });

  await page.route(/.*\/event-api\/api\/v1\/eventos(\?.*)?$/, async (route) => {
    const method = route.request().method();
    if (method === 'GET') {
      await json(route, events);
      return;
    }

    if (method === 'POST') {
      const body = route.request().postDataJSON() as Partial<typeof event>;
      createdSequence += 1;
      const created = {
        ...event,
        ...body,
        id: `00000000-0000-0000-0000-000000000${createdSequence}`,
        cupoDisponible: body.cupoMaximo ?? event.cupoMaximo,
        estado: 'BORRADOR',
        organizadorId: '33333333-3333-3333-3333-333333333333',
        aceptaInscripciones: false,
      };
      events.push(created);
      await json(route, created, 201);
      return;
    }

    await json(route, { message: 'Metodo no soportado' }, 405);
  });

  await page.route(/.*\/event-api\/api\/v1\/eventos\/([^/?]+)$/, async (route) => {
    const method = route.request().method();
    const eventoId = new URL(route.request().url()).pathname.split('/').pop();
    const index = events.findIndex((candidate) => candidate.id === eventoId);

    if (method === 'GET') {
      await json(route, index >= 0 ? events[index] : event);
      return;
    }

    if (method === 'PUT') {
      const body = route.request().postDataJSON() as Partial<typeof event>;
      const updated = {
        ...(index >= 0 ? events[index] : event),
        ...body,
        id: eventoId ?? eventId,
        cupoDisponible: body.cupoMaximo ?? (index >= 0 ? events[index].cupoDisponible : event.cupoDisponible),
      };
      if (index >= 0) {
        events[index] = updated;
      } else {
        events.push(updated);
      }
      await json(route, updated);
      return;
    }

    if (method === 'DELETE') {
      if (index >= 0) events.splice(index, 1);
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    await json(route, { message: 'Metodo no soportado' }, 405);
  });

  await page.route('**/event-api/api/v1/tarifas**', async (route) => {
    await json(route, [tariff]);
  });

  await page.route('**/event-api/api/v1/eventos/*/publicar', async (route) => {
    await route.fulfill({ status: 204, body: '' });
  });

  await page.route('**/event-api/api/v1/eventos/*/cancelar', async (route) => {
    await route.fulfill({ status: 204, body: '' });
  });

  await page.route('**/inscription-api/api/v1/inscripciones', async (route) => {
    await json(route, {
      inscripcionId: inscriptionId,
      eventoId: eventId,
      estado: 'PENDIENTE_PAGO',
      fechaInscripcion: '2026-05-31T12:00:00Z',
      fechaExpiracionPago: '2033-05-31T12:15:00Z',
      checkoutUrl: 'https://wiremock.local/checkout',
      expiraEnSegundos: 900,
    }, 201);
  });

  await page.route('**/payment-api/api/v1/webhooks/pagos', async (route) => {
    await json(route, { resultado: 'CONFIRMADO' });
  });
}

export async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel(/email/i).fill('diego.participante@javeriana.edu.co');
  await page.getByLabel(/contraseña/i).fill('demo123');
  await page.getByRole('button', { name: /iniciar sesión/i }).click();
}

export async function loginAs(page: Page, user: DemoUser) {
  await page.goto('/login');
  await page.getByLabel(/email/i).fill(user.email);
  await page.getByLabel(/contraseña/i).fill(user.password);
  await page.getByRole('button', { name: /iniciar sesión/i }).click();
}
