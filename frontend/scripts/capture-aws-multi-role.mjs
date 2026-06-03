import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

const baseUrl = process.env.AWS_SPA_URL ?? 'https://d1xvny1kolb55e.cloudfront.net';
const outputDir = resolve(process.env.AWS_MULTI_ROLE_SCREENSHOT_DIR ?? '../docs/evidence/aws-productivo/multi-rol');

const users = [
  {
    role: 'ADMIN',
    slug: 'admin',
    name: 'Ana Administradora',
    email: 'ana.admin@javeriana.edu.co',
    password: 'demo123',
  },
  {
    role: 'ORGANIZADOR',
    slug: 'organizador',
    name: 'Carlos Organizador',
    email: 'carlos.organizador@javeriana.edu.co',
    password: 'demo123',
  },
  {
    role: 'PARTICIPANTE',
    slug: 'participante',
    name: 'Sofia Mesa de Ayuda',
    email: 'sofia.soporte@javeriana.edu.co',
    password: 'demo123',
  },
];

mkdirSync(outputDir, { recursive: true });

const browser = await chromium.launch({ headless: true });

try {
  for (const user of users) {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      deviceScaleFactor: 1,
    });
    const page = await context.newPage();

    await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' });
    await page.getByLabel(/email/i).fill(user.email);
    await page.getByLabel(/contraseña/i).fill(user.password);
    await page.getByRole('button', { name: /iniciar sesión/i }).click();
    await page.waitForURL(/\/catalogo/, { timeout: 20000 });
    await page.waitForSelector('.session-chip', { timeout: 20000 });
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => undefined);

    const sessionText = await page.locator('.session-chip').innerText();
    if (!sessionText.includes(user.name)) {
      throw new Error(`Header no muestra el usuario esperado para ${user.role}. Header=${sessionText}`);
    }

    await page.screenshot({
      path: `${outputDir}/${user.slug}-catalogo-aws.png`,
      fullPage: true,
    });
    await context.close();

    console.log(`[capture-aws-multi-role] ${user.role}: ${outputDir}/${user.slug}-catalogo-aws.png`);
  }
} finally {
  await browser.close();
}
