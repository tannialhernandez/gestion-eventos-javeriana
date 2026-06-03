import { expect, test } from '@playwright/test';
import { demoUsers, evidenceDir, loginAs, mockBackend, prepareEvidenceDir } from './helpers';

const organizador = demoUsers.find((user) => user.roles.includes('ORGANIZADOR')) ?? demoUsers[2];

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test('organizador crea evento desde el formulario administrativo', async ({ page }) => {
  await loginAs(page, organizador);

  await page.getByRole('button', { name: /crear evento/i }).click();
  await expect(page).toHaveURL(/\/eventos\/nuevo/);

  await page.getByLabel(/título/i).fill('Foro E2E de Patrones');
  await page.getByLabel(/descripción/i).fill('Evento creado por Playwright para validar CRUD multi-rol.');
  await page.getByLabel(/fecha inicio/i).fill('2033-09-10');
  await page.getByLabel(/fecha fin/i).fill('2033-09-11');
  await page.getByLabel(/fecha límite de inscripción/i).fill('2033-09-01T23:59');
  await page.getByLabel(/lugar/i).fill('Auditorio Javeriana');
  await page.getByLabel(/capacidad/i).fill('80');
  await page.getByLabel(/tarifa monto/i).fill('120000');
  await page.getByLabel(/estado/i).selectOption('PENDIENTE_PUBLICACION');
  await page.getByRole('button', { name: /guardar evento/i }).click();

  await expect(page).toHaveURL(/\/eventos\/00000000-0000-0000-0000-000000000901/);
  await expect(page.getByRole('heading', { name: /foro e2e de patrones/i })).toBeVisible();
  await page.screenshot({ path: `${evidenceDir}/organizador-crea-evento.png`, fullPage: true });
});
