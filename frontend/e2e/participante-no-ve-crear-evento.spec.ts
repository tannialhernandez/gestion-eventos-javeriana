import { expect, test } from '@playwright/test';
import { demoUsers, evidenceDir, loginAs, mockBackend, prepareEvidenceDir } from './helpers';

const participante = demoUsers.find((user) => user.email === 'diego.participante@javeriana.edu.co') ?? demoUsers[0];

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test('participante no ve crear ni editar y ruta nueva queda restringida', async ({ page }) => {
  await loginAs(page, participante);

  await expect(page).toHaveURL(/\/catalogo/);
  await expect(page.getByRole('heading', { name: /eventos disponibles/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /crear evento/i })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /editar/i })).toHaveCount(0);

  await page.goto('/eventos/nuevo');
  await expect(page).toHaveURL(/\/catalogo/);
  await expect(page.getByRole('alert')).toContainText('Acceso restringido');
  await page.screenshot({ path: `${evidenceDir}/participante-no-ve-crear-evento.png`, fullPage: true });
});
