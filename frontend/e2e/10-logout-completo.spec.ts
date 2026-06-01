import { expect, test } from '@playwright/test';
import { authEvidenceDir, eventId, login, mockBackend, prepareEvidenceDir } from './helpers';

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test.describe('Logout completo', () => {
  test('logout limpia sessionStorage y redirige', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL(/\/catalogo/);

    const tokenAntes = await page.evaluate(() => sessionStorage.getItem('gea.session.v1'));
    expect(tokenAntes).toBeTruthy();

    await page.getByRole('button', { name: /cerrar sesión/i }).click();

    await expect(page).toHaveURL(/\/login/);
    const tokenDespues = await page.evaluate(() => sessionStorage.getItem('gea.session.v1'));
    const usuarioDespues = await page.evaluate(() => sessionStorage.getItem('gea.user.v1'));
    expect(tokenDespues).toBeNull();
    expect(usuarioDespues).toBeNull();

    await page.screenshot({
      path: `${authEvidenceDir}/10-logout-completo.png`,
      fullPage: true,
    });
  });

  test('navegar atras tras logout no recupera sesion', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL(/\/catalogo/);
    await page.goto(`/eventos/${eventId}`);
    await expect(page).toHaveURL(/\/eventos\//);

    await page.getByRole('button', { name: /cerrar sesión/i }).click();
    await expect(page).toHaveURL(/\/login/);

    await page.goBack();
    await expect(page).toHaveURL(/\/login/);
  });
});
