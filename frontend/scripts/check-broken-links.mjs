import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

const appBaseUrl = process.env.SPA_BASE_URL ?? 'http://127.0.0.1:3000';
const reportPath = resolve('../docs/reporte-enlaces-frontend.md');

const eventId = '00000000-0000-0000-0000-000000000001';
const tariffId = '00000000-0000-0000-0001-000000000001';
const inscriptionId = 'insc-link-check';
const token =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLWRlbW8tMDAxIiwiZW1haWwiOiJkaWVnby5wYXJ0aWNpcGFudGVAamF2ZXJpYW5hLmVkdS5jbyIsIm5hbWUiOiJEaWVnbyBQYXJ0aWNpcGFudGUiLCJyb2xlcyI6WyJQQVJUSUNJUEFOVEUiXSwiZXhwIjoxOTk5OTk5OTk5fQ.mock-signature';

const user = {
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

const tariff = {
  id: tariffId,
  descripcion: 'Tarifa general',
  monto: 150000,
  moneda: 'COP',
};

const checkoutSnapshot = {
  eventTitle: event.titulo,
  eventId,
  tariffId,
  amount: tariff.monto,
  currency: tariff.moneda,
  checkoutUrl: 'https://wiremock.local/checkout',
  expiresAt: '2033-05-31T12:15:00Z',
};

const routesToCheck = [
  { path: '/login', authenticated: false },
  { path: '/', authenticated: true },
  { path: '/catalogo', authenticated: true },
  { path: `/eventos/${eventId}`, authenticated: true },
  { path: `/inscripciones/${inscriptionId}/pago`, authenticated: true, checkout: true },
  { path: `/confirmacion/${inscriptionId}`, authenticated: true, checkout: true },
  { path: '/ruta-inexistente', authenticated: true },
];

const internalRoutePatterns = [
  /^\/$/,
  /^\/login$/,
  /^\/catalogo$/,
  /^\/eventos\/[^/]+$/,
  /^\/inscripciones\/[^/]+\/pago$/,
  /^\/confirmacion\/[^/]+$/,
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

async function fulfillJson(route, body, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

async function setupApi(page) {
  await page.route('**/auth-api/api/v1/auth/login', async (route) => {
    await fulfillJson(route, {
      accessToken: token,
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
  await page.route(/.*\/event-api\/api\/v1\/eventos(\?.*)?$/, async (route) => fulfillJson(route, [event]));
  await page.route(/.*\/event-api\/api\/v1\/eventos\/.+$/, async (route) => fulfillJson(route, event));
  await page.route('**/event-api/api/v1/tarifas**', async (route) => fulfillJson(route, [tariff]));
  await page.route('**/inscription-api/api/v1/inscripciones', async (route) => fulfillJson(route, {
    inscripcionId,
    eventoId: eventId,
    estado: 'PENDIENTE_PAGO',
    fechaInscripcion: '2026-05-31T12:00:00Z',
    fechaExpiracionPago: checkoutSnapshot.expiresAt,
    checkoutUrl: checkoutSnapshot.checkoutUrl,
    expiraEnSegundos: 900,
  }, 201));
  await page.route('**/payment-api/api/v1/webhooks/pagos', async (route) => fulfillJson(route, { resultado: 'CONFIRMADO' }));
}

async function configureSession(context, routeInfo) {
  await context.addInitScript(({ isAuthenticated, sessionUser, sessionToken, shouldSetCheckout, checkoutData, currentInscriptionId }) => {
    if (isAuthenticated) {
      sessionStorage.setItem('gea.session.v1', JSON.stringify({
        token: sessionToken,
        expiresAt: '2033-05-31T12:00:00Z',
      }));
      sessionStorage.setItem('gea.user.v1', JSON.stringify(sessionUser));
    }
    if (shouldSetCheckout) {
      sessionStorage.setItem(`gea.checkout.${currentInscriptionId}`, JSON.stringify(checkoutData));
    }
  }, {
    isAuthenticated: routeInfo.authenticated,
    sessionUser: user,
    sessionToken: token,
    shouldSetCheckout: routeInfo.checkout,
    checkoutData: checkoutSnapshot,
    currentInscriptionId: inscriptionId,
  });
}

function classifyHref(href) {
  const url = new URL(href, appBaseUrl);
  const appOrigin = new URL(appBaseUrl).origin;

  if (url.origin !== appOrigin) {
    return { type: 'external', valid: true, normalized: url.href, reason: 'External link documented as allowed.' };
  }

  const path = url.pathname;
  const valid = internalRoutePatterns.some((pattern) => pattern.test(path));
  return {
    type: 'internal',
    valid,
    normalized: path,
    reason: valid ? 'Matches SPA route inventory.' : 'Does not match any SPA route.',
  };
}

let server;
let browser;
const findings = [];

try {
  server = await ensureServer();
  browser = await chromium.launch({ headless: true });

  for (const routeInfo of routesToCheck) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await configureSession(context, routeInfo);
    const page = await context.newPage();
    await setupApi(page);
    await page.goto(new URL(routeInfo.path, appBaseUrl).toString(), { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(750);

    const links = await page.locator('a[href]').evaluateAll((anchors) => anchors.map((anchor) => ({
      text: anchor.textContent?.replace(/\s+/g, ' ').trim() || '(sin texto)',
      href: anchor.getAttribute('href') ?? '',
      absoluteHref: anchor.href,
    })));

    for (const link of links) {
      findings.push({
        sourceRoute: routeInfo.path,
        text: link.text,
        href: link.href,
        ...classifyHref(link.absoluteHref),
      });
    }

    await context.close();
  }

  const broken = findings.filter((finding) => !finding.valid);
  const rows = findings.map((finding) => `| \`${finding.sourceRoute}\` | ${finding.text} | \`${finding.href}\` | ${finding.type} | ${finding.valid ? 'OK' : 'ROTO'} | ${finding.reason} |`).join('\n');
  const report = `# Reporte de Enlaces Frontend

Fecha de ejecucion: ${new Date().toISOString()}

## Resumen

| Metrica | Resultado |
|---|---:|
| Rutas recorridas | ${routesToCheck.length} |
| Enlaces encontrados | ${findings.length} |
| Enlaces rotos | ${broken.length} |

## Criterio de validacion

Un enlace interno es valido si resuelve a una ruta declarada por el SPA:

- \`/\`
- \`/login\`
- \`/catalogo\`
- \`/eventos/:eventoId\`
- \`/inscripciones/:inscripcionId/pago\`
- \`/confirmacion/:inscripcionId\`

Los enlaces externos se documentan como permitidos cuando apuntan fuera del origen del SPA, por ejemplo el checkout simulado.

## Detalle

| Ruta origen | Texto | Href | Tipo | Estado | Observacion |
|---|---|---|---|---|---|
${rows || '| N/A | N/A | N/A | N/A | OK | No se encontraron enlaces en las rutas recorridas. |'}

## Resultado

${broken.length === 0 ? 'No se detectaron enlaces internos rotos.' : `Se detectaron ${broken.length} enlaces internos rotos que requieren correccion.`}
`;

  mkdirSync(resolve('../docs'), { recursive: true });
  writeFileSync(reportPath, report);

  if (broken.length > 0) {
    throw new Error(`Broken links detected: ${broken.map((finding) => `${finding.sourceRoute} -> ${finding.href}`).join(', ')}`);
  }

  console.log(`Checked ${findings.length} links. Broken links: 0. Report: ${reportPath}`);
} finally {
  if (browser) await browser.close();
  if (server) server.kill('SIGTERM');
}
