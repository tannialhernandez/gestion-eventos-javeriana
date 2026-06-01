import { expect, test } from '@playwright/test';
import { authEvidenceDir, evidenceDir, login, mockBackend, prepareEvidenceDir } from './helpers';

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test.describe('Flujo de login', () => {
  test('login con credenciales validas redirige a catalogo', async ({ page }) => {
    await login(page);

    await expect(page).toHaveURL(/\/catalogo/);
    await expect(page.getByText('Pontificia Universidad Javeriana')).toBeVisible();
    await page.screenshot({ path: `${evidenceDir}/01-login-flow.png`, fullPage: true });
  });

  test('login con credenciales invalidas muestra error', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill('falso@javeriana.edu.co');
    await page.getByLabel(/contraseña/i).fill('wrong123');
    await page.getByRole('button', { name: /iniciar sesión/i }).click();

    const alerta = page.getByRole('alert');
    await expect(alerta).toBeVisible();
    await expect(alerta).toContainText(/credenciales invalidas/i);
    await page.screenshot({
      path: `${authEvidenceDir}/08-credenciales-invalidas.png`,
      fullPage: true,
    });
  });

  test('email malformado rechaza submit antes del backend', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill('no-es-email');
    await page.getByLabel(/contraseña/i).fill('demo123');
    await page.getByRole('button', { name: /iniciar sesión/i }).click();

    await expect(page).toHaveURL(/\/login/);
    const validationMessage = await page.getByLabel(/email/i).evaluate((element) => {
      return (element as HTMLInputElement).validationMessage;
    });
    expect(validationMessage.length).toBeGreaterThan(0);
  });

  test('logout limpia sesion y redirige a login', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL(/\/catalogo/);

    await page.getByRole('button', { name: /cerrar sesión/i }).click();

    await expect(page).toHaveURL(/\/login/);
  });
});
