import { mkdirSync, writeFileSync } from 'node:fs';
import { AxeBuilder } from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { eventId, evidenceDir, login, mockBackend, prepareEvidenceDir } from './helpers';

const accessibilityDir = 'test-results/accessibility';
const wcagTags = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

async function expectAccessible(page: Page, name: string) {
  const results = await new AxeBuilder({ page })
    .withTags(wcagTags)
    .analyze();

  writeFileSync(
    `${accessibilityDir}/${name}.json`,
    JSON.stringify(
      {
        url: page.url(),
        tags: wcagTags,
        violations: results.violations,
        passes: results.passes.length,
        incomplete: results.incomplete.length,
      },
      null,
      2,
    ),
  );
  await page.screenshot({ path: `${evidenceDir}/06-a11y-${name}.png`, fullPage: true });

  expect(results.violations).toEqual([]);
}

test.beforeEach(async ({ page }) => {
  mkdirSync(accessibilityDir, { recursive: true });
  await prepareEvidenceDir();
  await mockBackend(page);
});

test.describe('Auditoría WCAG 2.1 AA automatizada', () => {
  test('login institucional no tiene violaciones axe', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /plataforma de gestión de eventos académicos/i })).toBeVisible();

    await expectAccessible(page, 'login');
  });

  test('catálogo autenticado no tiene violaciones axe', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL(/\/catalogo/);
    await expect(page.getByRole('heading', { name: /congreso de arquitectura 2026/i })).toBeVisible();

    await expectAccessible(page, 'catalogo');
  });

  test('detalle de evento e inscripción no tienen violaciones axe', async ({ page }) => {
    await login(page);
    await page.goto(`/eventos/${eventId}`);
    await expect(page.getByRole('heading', { name: /congreso de arquitectura 2026/i })).toBeVisible();

    await expectAccessible(page, 'detalle-evento');
  });

  test('pago simulado no tiene violaciones axe', async ({ page }) => {
    await login(page);
    await page.goto(`/eventos/${eventId}`);
    await page.getByRole('button', { name: /inscribirme y pagar/i }).click();
    await expect(page).toHaveURL(/\/pago/);
    await expect(page.getByRole('heading', { name: /resumen/i })).toBeVisible();

    await expectAccessible(page, 'pago');
  });
});
