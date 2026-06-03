import { expect, test } from '@playwright/test';
import { eventId, evidenceDir, login, mockBackend, prepareEvidenceDir } from './helpers';

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
  await page.route('**/inscription-api/api/v1/inscripciones', async (route) => {
    await route.fulfill({
      status: 409,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'Sin cupos disponibles' }),
    });
  });
});

test('cupo agotado muestra mensaje contextual', async ({ page }) => {
  await login(page);
  await page.goto(`/eventos/${eventId}`);
  await expect(page.getByRole('heading', { name: /congreso de arquitectura 2026/i })).toBeVisible();

  await page.getByRole('button', { name: /^inscribirme$/i }).click();

  await expect(page.getByRole('alert')).toContainText(/cupo agotado/i);
  await page.screenshot({ path: `${evidenceDir}/04-cupo-agotado.png`, fullPage: true });
});
