import { expect, test } from '@playwright/test';
import { evidenceDir, mockBackend, prepareEvidenceDir } from './helpers';

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test('token expirado redirige a login', async ({ page }) => {
  await page.addInitScript(() => {
    window.sessionStorage.setItem('gea.session.v1', JSON.stringify({
      token: 'expired-token',
      expiresAt: '2020-01-01T00:00:00Z',
    }));
    window.sessionStorage.setItem('gea.user.v1', JSON.stringify({
      id: 'user-demo-001',
      name: 'Diego Participante',
      email: 'diego.participante@javeriana.edu.co',
      roles: ['PARTICIPANTE'],
    }));
  });

  await page.goto('/catalogo');

  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole('heading', { name: /plataforma de gestión de eventos académicos/i })).toBeVisible();
  await page.screenshot({ path: `${evidenceDir}/03-sesion-expirada.png`, fullPage: true });
});
