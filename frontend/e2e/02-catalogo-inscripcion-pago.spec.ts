import { expect, test } from '@playwright/test';
import { eventId, evidenceDir, inscriptionId, login, mockBackend, prepareEvidenceDir } from './helpers';

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test('flujo critico catalogo -> inscripcion -> pago -> confirmacion', async ({ page }) => {
  await login(page);
  await expect(page).toHaveURL(/\/catalogo/);

  await expect(page.getByRole('heading', { name: /congreso de arquitectura 2026/i })).toBeVisible();
  await page.getByRole('button', { name: /ver detalle/i }).click();
  await expect(page).toHaveURL(new RegExp(`/eventos/${eventId}`));

  await page.getByRole('button', { name: /inscribirme y pagar/i }).click();
  await expect(page).toHaveURL(new RegExp(`/inscripciones/${inscriptionId}/pago`));

  await page.getByRole('button', { name: /confirmar pago aprobado/i }).click();
  await expect(page).toHaveURL(new RegExp(`/confirmacion/${inscriptionId}`));
  await expect(page.getByRole('heading', { name: /inscripción en proceso de confirmación/i })).toBeVisible();
  await page.screenshot({ path: `${evidenceDir}/02-catalogo-inscripcion-pago.png`, fullPage: true });
});
