import { expect, test } from '@playwright/test';
import { authEvidenceDir, demoUsers, loginAs, mockBackend, prepareEvidenceDir } from './helpers';

const roleLabels = {
  ADMIN: 'Administrador',
  ORGANIZADOR: 'Organizador',
  PARTICIPANTE: 'Participante',
} as const;

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test.describe('Autenticacion con usuarios demo Javeriana', () => {
  for (const user of demoUsers) {
    test(`login con ${user.roles.join(', ')} (${user.email})`, async ({ page }) => {
      await loginAs(page, user);

      await expect(page).toHaveURL(/\/catalogo/);
      await expect(page.getByText(user.name)).toBeVisible();
      await expect(page.getByText('JWT activo')).toBeVisible();
      await expect(page.locator('.role-badge').filter({ hasText: roleLabels[user.roles[0]] })).toBeVisible();

      await page.screenshot({
        path: `${authEvidenceDir}/07-login-${user.slug}.png`,
        fullPage: true,
      });
    });
  }
});
