import { expect, test } from '@playwright/test';
import { evidenceDir, login, mockBackend, prepareEvidenceDir } from './helpers';

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
  await page.route('**/event-api/api/v1/eventos**', async (route) => {
    await route.fulfill({
      status: 503,
      contentType: 'application/json',
      headers: { 'Retry-After': '1' },
      body: JSON.stringify({ message: 'Circuit Breaker abierto' }),
    });
  });
});

test('503 con Retry-After muestra banner degradado y cuenta regresiva', async ({ page }) => {
  await login(page);

  await expect(page.getByRole('status')).toContainText(/servicio en degradación controlada/i);
  await expect(page.getByRole('alert')).toContainText(/servicio temporalmente no disponible/i);
  await expect(page.getByRole('alert').getByText(/reintento seguro|puedes intentar nuevamente/i)).toBeVisible();
  await page.screenshot({ path: `${evidenceDir}/05-circuit-breaker-ui.png`, fullPage: true });
});
