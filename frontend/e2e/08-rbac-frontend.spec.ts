import { expect, test } from '@playwright/test';
import { authEvidenceDir, demoUsers, eventId, loginAs, mockBackend, prepareEvidenceDir } from './helpers';

const participante = demoUsers.find((user) => user.email === 'diego.participante@javeriana.edu.co') ?? demoUsers[0];

test.beforeEach(async ({ page }) => {
  await prepareEvidenceDir();
  await mockBackend(page);
});

test.describe('RBAC Frontend', () => {
  test('PARTICIPANTE puede acceder a /catalogo', async ({ page }) => {
    await loginAs(page, participante);

    await expect(page).toHaveURL(/\/catalogo/);
    await expect(page.getByRole('heading', { name: /eventos disponibles/i })).toBeVisible();
    await expect(page.getByText(participante.name)).toBeVisible();
  });

  test('PARTICIPANTE puede crear inscripcion', async ({ page }) => {
    await loginAs(page, participante);
    await page.goto(`/eventos/${eventId}`);

    const submit = page.getByRole('button', { name: /inscribirme y pagar/i });
    await expect(submit).toBeEnabled();
    await submit.click();

    await expect(page).toHaveURL(/\/inscripciones\/insc-e2e-uuid\/pago/);
  });

  test('PARTICIPANTE no accede a rutas administrativas no declaradas', async ({ page }) => {
    await loginAs(page, participante);
    await page.goto('/admin');

    await expect(page).toHaveURL(/\/catalogo/);
    await expect(page.getByRole('heading', { name: /eventos disponibles/i })).toBeVisible();
  });

  test('acceso sin login redirige a /login', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/catalogo');

    await expect(page).toHaveURL(/\/login/);
    await page.screenshot({
      path: `${authEvidenceDir}/08-sin-login-redirige.png`,
      fullPage: true,
    });
  });
});
