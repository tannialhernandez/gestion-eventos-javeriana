import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { chromium } from 'playwright';

const appBaseUrl = process.env.SPA_BASE_URL ?? 'http://127.0.0.1:3000';
const outputDir = resolve(process.env.SPA_FULL_SCREENSHOT_DIR ?? 'evidence/full');
const shouldClean = process.env.SPA_FULL_SCREENSHOT_CLEAN !== 'false';

const eventId = '00000000-0000-0000-0000-000000000001';
const soldOutEventId = '00000000-0000-0000-0000-000000000099';
const tariffId = '00000000-0000-0000-0001-000000000001';
const inscriptionId = 'insc-visual-uuid';
const token =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLWRlbW8tMDAxIiwiZW1haWwiOiJkaWVnby5wYXJ0aWNpcGFudGVAamF2ZXJpYW5hLmVkdS5jbyIsIm5hbWUiOiJEaWVnbyBQYXJ0aWNpcGFudGUiLCJyb2xlcyI6WyJQQVJUSUNJUEFOVEUiXSwiZXhwIjoxOTk5OTk5OTk5fQ.mock-signature';

const authUser = {
  id: 'user-demo-001',
  name: 'Diego Participante',
  email: 'diego.participante@javeriana.edu.co',
  roles: ['PARTICIPANTE'],
};

const event = {
  id: eventId,
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

const soldOutEvent = {
  ...event,
  id: soldOutEventId,
  titulo: 'Taller sin cupos',
  descripcion: 'Sesion practica con cupos agotados para validar UX de negocio.',
  cupoMaximo: 10,
  cupoDisponible: 0,
  aceptaInscripciones: false,
};

const tariff = {
  id: tariffId,
  descripcion: 'Tarifa general',
  monto: 150000,
  moneda: 'COP',
};

const inscription = {
  inscripcionId: inscriptionId,
  eventoId: eventId,
  estado: 'PENDIENTE_PAGO',
  fechaInscripcion: '2026-05-31T12:00:00Z',
  fechaExpiracionPago: '2033-05-31T12:15:00Z',
  checkoutUrl: 'https://wiremock.local/checkout',
  expiraEnSegundos: 900,
};

const checkoutSnapshot = {
  eventTitle: event.titulo,
  eventId,
  tariffId,
  amount: tariff.monto,
  currency: tariff.moneda,
  checkoutUrl: inscription.checkoutUrl,
  expiresAt: inscription.fechaExpiracionPago,
};

const viewports = [
  { name: 'mobile', width: 375, height: 667 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'desktop', width: 1440, height: 900 },
];

const scenarios = [
  {
    id: 'login-default',
    route: '/login',
    routePattern: '/login',
    state: 'default',
    description: 'Pantalla de login institucional con usuarios demo.',
    waitFor: async (page) => page.getByRole('heading', { name: /plataforma de gestion de eventos academicos|plataforma de gestión de eventos académicos/i }).waitFor(),
  },
  {
    id: 'login-error',
    route: '/login',
    routePattern: '/login',
    state: 'error-credenciales',
    description: 'Login con credenciales invalidas y alerta accesible.',
    act: async (page) => {
      await page.getByLabel(/email/i).fill('falso@javeriana.edu.co');
      await page.getByLabel(/contraseña/i).fill('wrong123');
      await page.getByRole('button', { name: /iniciar sesion|iniciar sesión/i }).click();
    },
    waitFor: async (page) => page.getByRole('alert').waitFor(),
  },
  {
    id: 'catalogo-success',
    route: '/catalogo',
    routePattern: '/catalogo',
    state: 'success',
    authenticated: true,
    description: 'Catalogo con eventos publicados.',
    waitFor: async (page) => page.getByRole('heading', { name: event.titulo }).waitFor(),
  },
  {
    id: 'catalogo-loading',
    route: '/catalogo',
    routePattern: '/catalogo',
    state: 'loading',
    authenticated: true,
    description: 'Skeleton inicial de catalogo antes de respuesta del backend.',
    api: { eventListDelayMs: 3000 },
    waitFor: async (page) => page.getByLabel('Cargando').waitFor(),
  },
  {
    id: 'catalogo-empty',
    route: '/catalogo',
    routePattern: '/catalogo',
    state: 'empty',
    authenticated: true,
    description: 'Catalogo sin eventos para los filtros seleccionados.',
    api: { events: [] },
    waitFor: async (page) => page.getByText(/no hay eventos publicados/i).waitFor(),
  },
  {
    id: 'catalogo-error',
    route: '/catalogo',
    routePattern: '/catalogo',
    state: 'error-servicio',
    authenticated: true,
    description: 'Error controlado de event-service con Retry-After.',
    api: { eventListStatus: 503 },
    waitFor: async (page) => page.getByText(/servicio temporalmente no disponible/i).waitFor(),
  },
  {
    id: 'detalle-success',
    route: `/eventos/${eventId}`,
    routePattern: '/eventos/:eventoId',
    state: 'success',
    authenticated: true,
    description: 'Detalle de evento con seleccion de tarifa e inscripcion disponible.',
    waitFor: async (page) => page.getByRole('button', { name: /inscribirme y pagar/i }).waitFor(),
  },
  {
    id: 'detalle-loading',
    route: `/eventos/${eventId}`,
    routePattern: '/eventos/:eventoId',
    state: 'loading',
    authenticated: true,
    description: 'Skeleton de detalle mientras se cargan evento y tarifas.',
    api: { eventDetailDelayMs: 3000, tariffDelayMs: 3000 },
    waitFor: async (page) => page.getByLabel('Cargando').waitFor(),
  },
  {
    id: 'detalle-sold-out',
    route: `/eventos/${soldOutEventId}`,
    routePattern: '/eventos/:eventoId',
    state: 'cupo-agotado',
    authenticated: true,
    description: 'Detalle de evento sin cupos disponibles.',
    waitFor: async (page) => page.getByRole('heading', { name: soldOutEvent.titulo }).waitFor(),
  },
  {
    id: 'detalle-inscription-conflict',
    route: `/eventos/${eventId}`,
    routePattern: '/eventos/:eventoId',
    state: 'error-cupo-409',
    authenticated: true,
    description: 'Error contextual cuando inscription-service responde cupo agotado.',
    api: { inscriptionStatus: 409 },
    act: async (page) => {
      await page.getByRole('button', { name: /inscribirme y pagar/i }).click();
    },
    waitFor: async (page) => page.getByRole('alert').waitFor(),
  },
  {
    id: 'detalle-degraded',
    route: `/eventos/${eventId}`,
    routePattern: '/eventos/:eventoId',
    state: 'degradado-503',
    authenticated: true,
    description: 'UX de degradacion controlada con Circuit Breaker y Retry-After.',
    api: { inscriptionStatus: 503 },
    act: async (page) => {
      await page.getByRole('button', { name: /inscribirme y pagar/i }).click();
    },
    waitFor: async (page) => page.locator('.resilience-banner--degraded').waitFor(),
  },
  {
    id: 'payment-success',
    route: `/inscripciones/${inscriptionId}/pago`,
    routePattern: '/inscripciones/:inscripcionId/pago',
    state: 'success',
    authenticated: true,
    checkout: true,
    description: 'Resumen de pago con checkout simulado.',
    waitFor: async (page) => page.getByRole('heading', { name: /resumen/i }).waitFor(),
  },
  {
    id: 'payment-empty',
    route: `/inscripciones/${inscriptionId}/pago`,
    routePattern: '/inscripciones/:inscripcionId/pago',
    state: 'empty',
    authenticated: true,
    description: 'Pago sin snapshot de inscripcion activa en sessionStorage.',
    waitFor: async (page) => page.getByText(/no hay una inscripcion activa|no hay una inscripción activa/i).waitFor(),
  },
  {
    id: 'payment-error',
    route: `/inscripciones/${inscriptionId}/pago`,
    routePattern: '/inscripciones/:inscripcionId/pago',
    state: 'error-servicio',
    authenticated: true,
    checkout: true,
    description: 'Error de pago con respuesta 503 y Retry-After.',
    api: { paymentStatus: 503 },
    act: async (page) => {
      await page.getByRole('button', { name: /confirmar pago aprobado/i }).click();
    },
    waitFor: async (page) => page.getByRole('alert').waitFor(),
  },
  {
    id: 'confirmation-success',
    route: `/confirmacion/${inscriptionId}`,
    routePattern: '/confirmacion/:inscripcionId',
    state: 'success',
    authenticated: true,
    checkout: true,
    description: 'Confirmacion con contexto de evento recuperado de sessionStorage.',
    waitFor: async (page) => page.getByRole('heading', { name: /inscripcion en proceso de confirmacion|inscripción en proceso de confirmación/i }).waitFor(),
  },
  {
    id: 'confirmation-generic',
    route: `/confirmacion/${inscriptionId}`,
    routePattern: '/confirmacion/:inscripcionId',
    state: 'sin-snapshot',
    authenticated: true,
    description: 'Confirmacion generica cuando no hay snapshot local.',
    waitFor: async (page) => page.getByText(/el pago fue notificado/i).waitFor(),
  },
  {
    id: 'root-redirect',
    route: '/',
    routePattern: '/',
    state: 'redirect-catalogo',
    authenticated: true,
    description: 'Ruta raiz redirige al catalogo protegido.',
    waitFor: async (page) => page.getByRole('heading', { name: event.titulo }).waitFor(),
  },
  {
    id: 'fallback-redirect',
    route: '/ruta-inexistente',
    routePattern: '*',
    state: 'redirect-catalogo',
    authenticated: true,
    description: 'Ruta desconocida redirige al catalogo protegido.',
    waitFor: async (page) => page.getByRole('heading', { name: event.titulo }).waitFor(),
  },
];

function sleep(ms) {
  return new Promise((resolveSleep) => setTimeout(resolveSleep, ms));
}

async function isServerUp() {
  try {
    const response = await fetch(appBaseUrl, { signal: AbortSignal.timeout(1000) });
    return response.ok;
  } catch {
    return false;
  }
}

async function waitForServer(processRef) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    if (await isServerUp()) return;
    if (processRef?.exitCode !== null && processRef?.exitCode !== undefined) {
      throw new Error(`Vite dev server exited early with code ${processRef.exitCode}`);
    }
    await sleep(300);
  }
  throw new Error(`Timed out waiting for SPA at ${appBaseUrl}`);
}

async function ensureServer() {
  if (await isServerUp()) return null;

  const server = spawn('npm', ['run', 'dev', '--', '--host', '127.0.0.1'], {
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, BROWSER: 'none' },
  });

  let output = '';
  server.stdout.on('data', (chunk) => {
    output += chunk.toString();
  });
  server.stderr.on('data', (chunk) => {
    output += chunk.toString();
  });

  try {
    await waitForServer(server);
  } catch (error) {
    server.kill('SIGTERM');
    throw new Error(`${error.message}\n${output}`);
  }

  return server;
}

async function fulfillJson(route, body, status = 200, headers = {}) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    headers,
    body: JSON.stringify(body),
  });
}

async function setupApi(page, options = {}) {
  await page.route('**/auth-api/api/v1/auth/login', async (route) => {
    const body = route.request().postDataJSON();
    if (body.email === authUser.email && body.password === 'demo123') {
      await fulfillJson(route, {
        accessToken: token,
        tokenType: 'Bearer',
        expiresAt: '2033-05-31T12:00:00Z',
        user: {
          id: authUser.id,
          nombre: authUser.name,
          email: authUser.email,
          roles: authUser.roles,
        },
      });
      return;
    }
    await fulfillJson(route, { message: 'Credenciales invalidas' }, 401);
  });

  await page.route(/.*\/event-api\/api\/v1\/eventos(\?.*)?$/, async (route) => {
    if (options.eventListDelayMs) await sleep(options.eventListDelayMs);
    if (options.eventListStatus === 503) {
      await fulfillJson(route, {
        error: 'servicio_no_disponible',
        message: 'Event-service no esta disponible temporalmente.',
      }, 503, { 'Retry-After': '0' });
      return;
    }
    await fulfillJson(route, options.events ?? [event, soldOutEvent]);
  });

  await page.route(/.*\/event-api\/api\/v1\/eventos\/.+$/, async (route) => {
    if (options.eventDetailDelayMs) await sleep(options.eventDetailDelayMs);
    const url = new URL(route.request().url());
    const requestedId = url.pathname.split('/').at(-1);
    await fulfillJson(route, requestedId === soldOutEventId ? soldOutEvent : event);
  });

  await page.route('**/event-api/api/v1/tarifas**', async (route) => {
    if (options.tariffDelayMs) await sleep(options.tariffDelayMs);
    await fulfillJson(route, [tariff]);
  });

  await page.route('**/inscription-api/api/v1/inscripciones', async (route) => {
    if (options.inscriptionStatus === 409) {
      await fulfillJson(route, { message: 'Sin cupos disponibles' }, 409);
      return;
    }
    if (options.inscriptionStatus === 503) {
      await fulfillJson(route, {
        error: 'servicio_no_disponible',
        message: 'Inscription-service activo en modo degradado.',
      }, 503, { 'Retry-After': '0' });
      return;
    }
    await fulfillJson(route, inscription, 201);
  });

  await page.route('**/payment-api/api/v1/webhooks/pagos', async (route) => {
    if (options.paymentStatus === 503) {
      await fulfillJson(route, {
        error: 'servicio_no_disponible',
        message: 'Payment-service no esta disponible temporalmente.',
      }, 503, { 'Retry-After': '0' });
      return;
    }
    await fulfillJson(route, { resultado: 'CONFIRMADO' });
  });
}

async function configureSession(context, scenario) {
  await context.addInitScript(({ isAuthenticated, user, tokenValue, shouldSetCheckout, checkoutKey, checkoutData }) => {
    if (isAuthenticated) {
      sessionStorage.setItem('gea.session.v1', JSON.stringify({
        token: tokenValue,
        expiresAt: '2033-05-31T12:00:00Z',
      }));
      sessionStorage.setItem('gea.user.v1', JSON.stringify(user));
    }
    if (shouldSetCheckout) {
      sessionStorage.setItem(checkoutKey, JSON.stringify(checkoutData));
    }
  }, {
    isAuthenticated: Boolean(scenario.authenticated),
    user: authUser,
    tokenValue: token,
    shouldSetCheckout: Boolean(scenario.checkout),
    checkoutKey: `gea.checkout.${inscriptionId}`,
    checkoutData: checkoutSnapshot,
  });
}

function slug(value) {
  return value
    .replace(/^\//, 'root-')
    .replace(/\*/g, 'fallback')
    .replace(/[:/]+/g, '-')
    .replace(/^-|-$/g, '')
    .toLowerCase();
}

async function captureScenario(browser, scenario, viewport) {
  const context = await browser.newContext({
    viewport: { width: viewport.width, height: viewport.height },
    deviceScaleFactor: 1,
  });
  await configureSession(context, scenario);

  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (
      message.type() === 'error' &&
      !message.text().includes('401') &&
      !message.text().includes('409') &&
      !message.text().includes('503')
    ) {
      errors.push(message.text());
    }
  });

  await setupApi(page, scenario.api);
  await page.goto(new URL(scenario.route, appBaseUrl).toString(), { waitUntil: 'domcontentloaded' });
  await page.addStyleTag({
    content: '*, *::before, *::after { animation: none !important; transition: none !important; caret-color: transparent !important; }',
  });

  if (scenario.act) await scenario.act(page);
  await scenario.waitFor(page);
  await page.waitForTimeout(250);

  const filename = `${scenario.id}-${viewport.name}.png`;
  const path = join(outputDir, filename);
  await page.screenshot({ path, fullPage: true });
  await context.close();

  if (errors.length > 0) {
    throw new Error(`${scenario.id}/${viewport.name} browser errors:\n${errors.join('\n')}`);
  }

  return {
    scenario: scenario.id,
    route: scenario.route,
    routePattern: scenario.routePattern,
    state: scenario.state,
    viewport: viewport.name,
    width: viewport.width,
    height: viewport.height,
    file: `frontend/evidence/full/${filename}`,
    description: scenario.description,
  };
}

function writeIndex(manifest) {
  const rows = manifest.captures.map((capture) => `
      <tr>
        <td>${capture.routePattern}</td>
        <td>${capture.state}</td>
        <td>${capture.viewport} (${capture.width}x${capture.height})</td>
        <td><a href="./${capture.file.split('/').at(-1)}">${capture.file.split('/').at(-1)}</a></td>
      </tr>`).join('');

  const sections = scenarios.map((scenarioItem) => {
    const images = viewports.map((viewport) => {
      const file = `${scenarioItem.id}-${viewport.name}.png`;
      return `
        <figure>
          <a href="./${file}"><img src="./${file}" alt="${scenarioItem.description} - ${viewport.name}" loading="lazy"></a>
          <figcaption>${viewport.name} (${viewport.width}x${viewport.height})</figcaption>
        </figure>`;
    }).join('');

    return `
      <section>
        <h2>${scenarioItem.routePattern} - ${scenarioItem.state}</h2>
        <p>${scenarioItem.description}</p>
        <div class="grid">${images}</div>
      </section>`;
  }).join('');

  writeFileSync(join(outputDir, 'index.html'), `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Evidencia visual completa SPA</title>
  <style>
    body { font-family: Inter, system-ui, sans-serif; margin: 0; color: #172033; background: #f4f7fb; }
    header, main { width: min(1180px, calc(100% - 32px)); margin: 0 auto; }
    header { padding: 32px 0 12px; }
    h1 { margin: 0 0 8px; }
    table { width: 100%; border-collapse: collapse; margin: 18px 0 32px; background: #fff; }
    th, td { padding: 10px 12px; border: 1px solid #d9e2ef; text-align: left; vertical-align: top; }
    th { background: #eef4ff; }
    section { margin: 0 0 36px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }
    figure { margin: 0; padding: 10px; background: #fff; border: 1px solid #d9e2ef; border-radius: 8px; }
    img { width: 100%; height: 260px; object-fit: contain; background: #f8fafc; border: 1px solid #eef2f7; }
    figcaption { margin-top: 8px; font-size: 13px; color: #667085; }
  </style>
</head>
<body>
  <header>
    <h1>Evidencia visual completa del frontend</h1>
    <p>${manifest.captures.length} capturas generadas en ${manifest.generatedAt}.</p>
  </header>
  <main>
    <table>
      <thead><tr><th>Ruta</th><th>Estado</th><th>Viewport</th><th>Archivo</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
    ${sections}
  </main>
</body>
</html>
`);
}

if (shouldClean && existsSync(outputDir)) {
  rmSync(outputDir, { recursive: true, force: true });
}
mkdirSync(outputDir, { recursive: true });

let server;
let browser;
try {
  server = await ensureServer();
  browser = await chromium.launch({ headless: true });
  const captures = [];

  for (const scenario of scenarios) {
    for (const viewport of viewports) {
      captures.push(await captureScenario(browser, scenario, viewport));
      console.log(`captured ${scenario.id} ${viewport.name}`);
    }
  }

  const manifest = {
    generatedAt: new Date().toISOString(),
    appBaseUrl,
    viewports,
    scenarioCount: scenarios.length,
    captureCount: captures.length,
    captures,
  };

  writeFileSync(join(outputDir, 'manifest.json'), JSON.stringify(manifest, null, 2));
  writeIndex(manifest);
  console.log(`Captured ${captures.length} screenshots in ${outputDir}`);
} finally {
  if (browser) await browser.close();
  if (server) server.kill('SIGTERM');
}
