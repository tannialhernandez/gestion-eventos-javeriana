import { mkdirSync } from 'node:fs';
import { expect, test, type Page, type Route } from '@playwright/test';
import { createMockJwt, demoUsers, loginAs } from './helpers';

const crudEvidenceDir = 'test-results/evidence/crud-eventos';
const organizer = demoUsers.find((user) => user.roles.includes('ORGANIZADOR')) ?? demoUsers[2];
const participant = demoUsers.find((user) => user.email === 'diego.participante@javeriana.edu.co') ?? demoUsers[0];
const baseEventId = '00000000-0000-0000-0000-000000000001';
const createdEventId = '00000000-0000-0000-0000-000000000901';

type MockEvent = {
  id: string;
  titulo: string;
  descripcion: string;
  tipo: string;
  modalidad: string;
  fechaInicio: string;
  fechaFin: string;
  fechaLimiteInscripcion: string;
  cupoMaximo: number;
  cupoDisponible: number;
  estado: string;
  organizadorId: string;
  aceptaInscripciones: boolean;
};

const baseEvent: MockEvent = {
  id: baseEventId,
  titulo: 'Congreso de Arquitectura 2026',
  descripcion: 'Encuentro academico sobre arquitectura de software.',
  tipo: 'CONGRESO',
  modalidad: 'HIBRIDO',
  fechaInicio: '2033-08-15T09:00:00Z',
  fechaFin: '2033-08-16T17:00:00Z',
  fechaLimiteInscripcion: '2033-08-01T23:59:59Z',
  cupoMaximo: 250,
  cupoDisponible: 120,
  estado: 'PUBLICADO',
  organizadorId: organizer.id,
  aceptaInscripciones: true,
};

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

async function mockCrudBackend(page: Page) {
  const events: MockEvent[] = [{ ...baseEvent }];

  await page.route('**/auth-api/api/v1/auth/login', async (route) => {
    const body = route.request().postDataJSON() as { email?: string; password?: string };
    const user = demoUsers.find((candidate) => candidate.email === body.email && candidate.password === body.password);

    if (!user) {
      await json(route, { message: 'Credenciales invalidas' }, 401);
      return;
    }

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
  });

  await page.route(/.*\/event-api\/api\/v1\/eventos(\?.*)?$/, async (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as Partial<MockEvent>;
      const created = {
        ...baseEvent,
        ...body,
        id: createdEventId,
        organizadorId: organizer.id,
        estado: 'BORRADOR',
        aceptaInscripciones: false,
        cupoDisponible: body.cupoMaximo ?? 80,
      };
      events.push(created);
      await json(route, created, 201);
      return;
    }

    await json(route, events);
  });

  await page.route(/.*\/event-api\/api\/v1\/eventos\/[^/]+\/publicar$/, async (route) => {
    const eventId = route.request().url().split('/eventos/')[1]?.split('/')[0];
    const event = events.find((candidate) => candidate.id === eventId);
    if (event) {
      event.estado = 'PUBLICADO';
      event.aceptaInscripciones = true;
    }
    await route.fulfill({ status: 204 });
  });

  await page.route(/.*\/event-api\/api\/v1\/eventos\/[^/]+$/, async (route) => {
    const eventId = route.request().url().split('/eventos/')[1]?.split('?')[0] ?? baseEventId;
    const event = events.find((candidate) => candidate.id === eventId) ?? events[0];

    if (route.request().method() === 'PUT') {
      const body = route.request().postDataJSON() as Partial<MockEvent>;
      Object.assign(event, body);
      await json(route, event);
      return;
    }

    if (route.request().method() === 'DELETE') {
      const index = events.findIndex((candidate) => candidate.id === eventId);
      if (index >= 0) events.splice(index, 1);
      await route.fulfill({ status: 204 });
      return;
    }

    await json(route, event);
  });

  await page.route('**/event-api/api/v1/tarifas**', async (route) => {
    await json(route, [{
      id: '00000000-0000-0000-0001-000000000001',
      descripcion: 'Tarifa general',
      monto: 150000,
      moneda: 'COP',
    }]);
  });
}

test.beforeEach(async ({ page }) => {
  mkdirSync(crudEvidenceDir, { recursive: true });
  await mockCrudBackend(page);
});

test('ORGANIZADOR crea evento y aparece en catálogo', async ({ page }) => {
  await loginAs(page, organizer);

  await page.getByRole('button', { name: /crear evento/i }).click();
  await expect(page).toHaveURL(/\/eventos\/nuevo/);

  await page.getByLabel(/título/i).fill('Evento Playwright Organizador');
  await page.getByLabel(/descripción/i).fill('Evento creado por rol organizador para validación E2E.');
  await page.getByLabel(/fecha inicio/i).fill('2033-09-10');
  await page.getByLabel(/fecha fin/i).fill('2033-09-11');
  await page.getByLabel(/fecha límite de inscripción/i).fill('2033-09-01T23:59');
  await page.getByLabel(/capacidad/i).fill('80');
  await page.getByLabel(/estado/i).selectOption('PUBLICADO');
  await page.getByRole('button', { name: /publicar evento/i }).click();

  await expect(page).toHaveURL(new RegExp(`/eventos/${createdEventId}`));
  await page.goto('/catalogo');
  await expect(page.getByRole('heading', { name: /evento playwright organizador/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /^editar$/i }).first()).toBeVisible();
  await page.screenshot({ path: `${crudEvidenceDir}/13-organizador-crea-evento.png`, fullPage: true });
});

test('PARTICIPANTE no ve Crear evento ni accede a /eventos/nuevo', async ({ page }) => {
  await loginAs(page, participant);

  await expect(page.getByRole('heading', { name: /eventos disponibles/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /crear evento/i })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^editar$/i })).toHaveCount(0);

  await page.goto('/eventos/nuevo');
  await expect(page).toHaveURL(/\/catalogo/);
  await page.screenshot({ path: `${crudEvidenceDir}/13-participante-sin-crud.png`, fullPage: true });
});
