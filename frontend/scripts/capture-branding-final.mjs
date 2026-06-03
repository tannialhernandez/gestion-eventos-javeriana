import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

const baseUrl = process.env.SPA_BASE_URL ?? 'https://d1xvny1kolb55e.cloudfront.net';
const outputDir = resolve(process.env.BRANDING_SCREENSHOT_DIR ?? '../docs/evidence/branding-final');

const users = [
  {
    role: 'ADMIN',
    label: 'Administrador',
    slug: 'admin',
    name: 'Ana Administradora',
    email: 'ana.admin@javeriana.edu.co',
    password: 'demo123',
  },
  {
    role: 'ORGANIZADOR',
    label: 'Organizador',
    slug: 'organizador',
    name: 'Carlos Organizador',
    email: 'carlos.organizador@javeriana.edu.co',
    password: 'demo123',
  },
  {
    role: 'PARTICIPANTE',
    label: 'Participante',
    slug: 'participante',
    name: 'Diego Participante',
    email: 'diego.participante@javeriana.edu.co',
    password: 'demo123',
  },
];

mkdirSync(outputDir, { recursive: true });

async function loginAs(page, user) {
  await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel(/email/i).fill(user.email);
  await page.getByLabel(/contraseña/i).fill(user.password);
  await page.getByRole('button', { name: /iniciar sesión/i }).click();
  await page.waitForURL(/\/catalogo/, { timeout: 25000 });
  await page.waitForSelector('.role-badge', { timeout: 25000 });
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => undefined);
}

const browser = await chromium.launch({ headless: true });

try {
  const loginContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const loginPage = await loginContext.newPage();
  await loginPage.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' });
  await loginPage.waitForSelector('#login-title', { timeout: 20000 });
  await loginPage.screenshot({ path: `${outputDir}/01-login-institucional.png`, fullPage: true });
  await loginContext.close();
  console.log(`[branding] login: ${outputDir}/01-login-institucional.png`);

  for (const user of users) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    await loginAs(page, user);

    const header = await page.locator('.topbar').innerText();
    if (!header.includes(user.name) || !header.includes(user.label)) {
      throw new Error(`Header no muestra usuario/rol esperado para ${user.role}. Header=${header}`);
    }

    await page.screenshot({
      path: `${outputDir}/02-catalogo-${user.slug}.png`,
      fullPage: true,
    });
    await context.close();
    console.log(`[branding] catalogo ${user.role}: ${outputDir}/02-catalogo-${user.slug}.png`);
  }

  const paymentContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const paymentPage = await paymentContext.newPage();
  await loginAs(paymentPage, users[2]);
  await paymentPage.evaluate(() => {
    sessionStorage.setItem('gea.checkout.branding-payment', JSON.stringify({
      eventTitle: 'Congreso de Arquitectura 2026',
      eventId: 'branding-event',
      tariffId: 'branding-tariff',
      amount: 150000,
      currency: 'COP',
      checkoutUrl: 'https://wiremock.local/checkout',
      expiresAt: '2033-05-31T12:00:00Z',
    }));
  });
  await paymentPage.goto(`${baseUrl}/inscripciones/branding-payment/pago`, { waitUntil: 'domcontentloaded' });
  await paymentPage.waitForSelector('.simulation-notice', { timeout: 20000 });
  await paymentPage.screenshot({ path: `${outputDir}/03-pago-simulacion.png`, fullPage: true });
  await paymentContext.close();
  console.log(`[branding] pago: ${outputDir}/03-pago-simulacion.png`);
} finally {
  await browser.close();
}
