import { expect, test } from '@playwright/test';
import { demoUsers, eventId, evidenceDir, loginAs, mockBackend, prepareEvidenceDir } from './helpers';

const admin = demoUsers.find((user) => user.roles.includes('ADMIN')) ?? demoUsers[3];

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test('admin edita evento de cualquier organizador', async ({ page }) => {
  await loginAs(page, admin);

  await expect(page).toHaveURL(/\/catalogo/);
  await expect(page.getByText('Vista de Administración - Gestión global')).toBeVisible();
  await expect(page.getByText('Admin').first()).toBeVisible();
  await expect(page.getByRole('button', { name: /editar/i })).toHaveCount(2);

  await page.goto(`/eventos/${eventId}/editar`);
  await expect(page.getByRole('heading', { name: /editar evento/i })).toBeVisible();
  await page.getByLabel(/título/i).fill('Congreso editado por Admin');
  await page.getByRole('button', { name: /guardar cambios/i }).click();

  await expect(page).toHaveURL(new RegExp(`/eventos/${eventId}`));
  await expect(page.getByRole('heading', { name: /congreso editado por admin/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /^editar$/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /eliminar/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /inscribirme/i })).toHaveCount(0);
  await page.screenshot({ path: `${evidenceDir}/admin-edita-cualquier-evento.png`, fullPage: true });
});
