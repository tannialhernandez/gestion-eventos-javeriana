import { expect, test } from '@playwright/test';
import { authEvidenceDir, login, mockBackend, prepareEvidenceDir } from './helpers';

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test.describe('Persistencia de sesion en refresh', () => {
  test('refresh con sesion activa mantiene autenticacion', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL(/\/catalogo/);

    const tokenAntes = await page.evaluate(() => sessionStorage.getItem('gea.session.v1'));
    expect(tokenAntes).toBeTruthy();

    await page.reload();

    await expect(page).toHaveURL(/\/catalogo/);
    await expect(page.getByText('Diego Participante')).toBeVisible();
    const tokenDespues = await page.evaluate(() => sessionStorage.getItem('gea.session.v1'));
    expect(tokenDespues).toBe(tokenAntes);

    await page.screenshot({
      path: `${authEvidenceDir}/11-refresh-session.png`,
      fullPage: true,
    });
  });
});
