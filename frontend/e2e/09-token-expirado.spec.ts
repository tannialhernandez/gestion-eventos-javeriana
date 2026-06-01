import { expect, test, type Page } from '@playwright/test';
import { authEvidenceDir, createMockJwt, demoUsers, mockBackend, prepareEvidenceDir } from './helpers';

const user = demoUsers[1];
const storedUser = {
  id: user.id,
  name: user.name,
  email: user.email,
  roles: user.roles,
};

async function installStoredSession(page: Page, token: string) {
  await page.addInitScript(({ sessionToken, sessionUser }) => {
    sessionStorage.setItem('gea.session.v1', JSON.stringify({
      token: sessionToken,
      expiresAt: '2033-05-31T12:00:00Z',
    }));
    sessionStorage.setItem('gea.user.v1', JSON.stringify(sessionUser));
  }, { sessionToken: token, sessionUser: storedUser });
}

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test.describe('Manejo de token expirado e invalido', () => {
  test('token expirado redirige a login y limpia sesion', async ({ page }) => {
    await installStoredSession(page, createMockJwt(user, Math.floor(Date.now() / 1000) - 60));

    await page.goto('/catalogo');

    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /ingreso institucional/i })).toBeVisible();
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('gea.session.v1'))).toBeNull();

    await page.screenshot({
      path: `${authEvidenceDir}/09-token-expirado.png`,
      fullPage: true,
    });
  });

  test('token malformado limpia sesion', async ({ page }) => {
    await installStoredSession(page, 'malformed-token-12345');

    await page.goto('/catalogo');

    await expect(page).toHaveURL(/\/login/);
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('gea.session.v1'))).toBeNull();
  });
});
