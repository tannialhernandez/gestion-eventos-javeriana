/**
 * Captura pantallas para material de presentación institucional.
 * Destino: docs/presentacion/capturas/
 */
import { chromium } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DEST = path.join(__dirname, '../../docs/presentacion/capturas');
const BASE = 'https://d1xvny1kolb55e.cloudfront.net';
const ALB  = 'eventos-javeriana-alb-1966078085.us-east-1.elb.amazonaws.com';

const USERS = {
  participante: { email: 'diego.participante@javeriana.edu.co', password: 'demo123' },
  organizador:  { email: 'carlos.organizador@javeriana.edu.co', password: 'demo123' },
  admin:        { email: 'ana.admin@javeriana.edu.co',           password: 'demo123' },
};

async function getToken(email, password) {
  const resp = await fetch(`http://${ALB}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const data = await resp.json();
  return data.accessToken;
}

async function shot(page, filename) {
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(DEST, filename), fullPage: false });
  console.log(`[ok] ${filename}`);
}

(async () => {
  const browser = await chromium.launch({ headless: true });

  // ── 1. Login page ─────────────────────────────────────────────
  {
    const page = await browser.newPage();
    await page.setViewportSize({ width: 1440, height: 900 });
    // Block Google Fonts to avoid font-load timeout
    await page.route('https://fonts.googleapis.com/**', r => r.abort());
    await page.route('https://fonts.gstatic.com/**',    r => r.abort());
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);
    await shot(page, '02-login-institucional.png');
    await page.close();
  }

  // ── Helper: login via browser form ────────────────────────────
  async function openAs(role) {
    const page = await browser.newPage();
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.route('https://fonts.googleapis.com/**', r => r.abort());
    await page.route('https://fonts.gstatic.com/**',    r => r.abort());
    const { email, password } = USERS[role];
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1000);
    // Fill email field
    await page.fill('input[type="email"]', email);
    await page.fill('input[type="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL('**/catalogo', { timeout: 15000 });
    await page.waitForTimeout(2500);
    return page;
  }

  // ── 2. Catálogo participante ───────────────────────────────────
  {
    const page = await openAs('participante');
    await shot(page, '03-catalogo-publicado.png');
    // Click first event
    const firstCard = page.locator('article.event-card').first();
    if (await firstCard.count() > 0) {
      const btn = firstCard.locator('button:has-text("Ver detalle")');
      if (await btn.count() > 0) await btn.click();
      await page.waitForTimeout(2000);
      await shot(page, '04-detalle-evento.png');
    }
    await page.close();
  }

  // ── 3. Catálogo organizador ────────────────────────────────────
  {
    const page = await openAs('organizador');
    await shot(page, '08-organizador-mis-eventos.png');
    // Create event page
    const btn = page.locator('button:has-text("Crear evento")');
    if (await btn.count() > 0) {
      await btn.click();
      await page.waitForTimeout(1500);
      await shot(page, '07-organizador-crear-evento.png');
    }
    await page.close();
  }

  // ── 4. Catálogo admin ──────────────────────────────────────────
  {
    const page = await openAs('admin');
    await shot(page, '09-admin-dashboard.png');
    // Try to open a BORRADOR event for approval shot
    const borradorCard = page.locator('article.event-card').first();
    if (await borradorCard.count() > 0) {
      const btn = borradorCard.locator('button:has-text("Ver detalle")');
      if (await btn.count() > 0) {
        await btn.click();
        await page.waitForTimeout(1500);
        await shot(page, '10-admin-aprobar-evento.png');
      }
    }
    await page.close();
  }

  await browser.close();
  console.log('\n✅ Capturas completadas en docs/presentacion/capturas/');
})();
