import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

const baseUrl = process.env.AWS_SPA_URL ?? 'https://d1xvny1kolb55e.cloudfront.net';
const outputDir = resolve(process.env.CRUD_EVENTOS_SCREENSHOT_DIR ?? '../docs/evidence/crud-eventos-multi-rol');

const users = {
  participante: {
    email: 'diego.participante@javeriana.edu.co',
    password: 'demo123',
    name: 'Diego Participante',
  },
  organizador: {
    email: 'carlos.organizador@javeriana.edu.co',
    password: 'demo123',
    name: 'Carlos Organizador',
  },
  admin: {
    email: 'ana.admin@javeriana.edu.co',
    password: 'demo123',
    name: 'Ana Administradora',
  },
};

mkdirSync(outputDir, { recursive: true });

async function login(page, user) {
  await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel(/email/i).fill(user.email);
  await page.getByLabel(/contraseña/i).fill(user.password);
  await page.getByRole('button', { name: /iniciar sesión/i }).click();
  await page.waitForURL(/\/catalogo/, { timeout: 25000 });
  await page.waitForSelector('.session-chip', { timeout: 25000 });
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => undefined);
  const header = await page.locator('.session-chip').innerText();
  if (!header.includes(user.name)) {
    throw new Error(`Sesion inesperada. Esperado=${user.name}. Header=${header}`);
  }
}

async function screenshot(page, name) {
  await page.screenshot({ path: `${outputDir}/${name}.png`, fullPage: true });
  console.log(`[capture-crud-eventos-multi-rol] ${outputDir}/${name}.png`);
}

async function withSession(browser, user, action) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    deviceScaleFactor: 1,
  });
  const page = await context.newPage();
  try {
    await login(page, user);
    await action(page);
  } finally {
    await context.close();
  }
}

const browser = await chromium.launch({ headless: true });

try {
  await withSession(browser, users.participante, async (page) => {
    await screenshot(page, '01-participante-sin-boton-crear');
  });

  await withSession(browser, users.organizador, async (page) => {
    await screenshot(page, '02-organizador-con-boton-crear');
    await page.getByRole('button', { name: /crear evento/i }).click();
    await page.waitForURL(/\/eventos\/nuevo/, { timeout: 10000 });
    await page.waitForSelector('form.event-form', { timeout: 10000 });
    await screenshot(page, '04-formulario-crear');
  });

  await withSession(browser, users.admin, async (page) => {
    await screenshot(page, '03-admin-con-todo');
    await screenshot(page, '06-lista-eventos');
    const firstEdit = page.getByRole('button', { name: /^editar$/i }).first();
    await firstEdit.waitFor({ timeout: 15000 });
    await firstEdit.click();
    await page.waitForURL(/\/eventos\/.*\/editar/, { timeout: 10000 });
    await page.waitForSelector('form.event-form', { timeout: 15000 });
    await screenshot(page, '05-formulario-editar');
  });
} finally {
  await browser.close();
}
