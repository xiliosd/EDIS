import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

// Usuario FICTICIO sembrado por db/dev/data.sql.
const RESPONDENT_USER = process.env.E2E_RESPONDENT_USER ?? 'PRUEBA01';
const RESPONDENT_PASSWORD = process.env.E2E_RESPONDENT_PASSWORD ?? 'Clave#01';
const WCAG_21_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

test('el servidor público ingresa y ve los capítulos de la encuesta', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Ingreso a la encuesta' })).toBeVisible();

  await page.getByLabel('Usuario').fill(RESPONDENT_USER);
  await page.getByLabel('Clave').fill(RESPONDENT_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();

  await expect(page.getByRole('heading', { name: 'Encuesta EDIN 2026' })).toBeVisible();
  await expect(page.getByText('B. Ubicación laboral')).toBeVisible();

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Ingreso a la encuesta' })).toBeVisible();
});

test('una clave errada muestra un mensaje claro', async ({ page }) => {
  await page.goto('/ingresar');
  await page.getByLabel('Usuario').fill(RESPONDENT_USER);
  await page.getByLabel('Clave').fill('errada');
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page.getByRole('alert')).toHaveText('Usuario o clave incorrectos.');
});

test('la pantalla de ingreso cumple WCAG 2.1 AA', async ({ page }) => {
  await page.goto('/ingresar');
  const results = await new AxeBuilder({ page }).withTags(WCAG_21_AA).analyze();
  expect(results.violations).toEqual([]);
});

test('la lista de capítulos cumple WCAG 2.1 AA', async ({ page }) => {
  await page.goto('/ingresar');
  await page.getByLabel('Usuario').fill(RESPONDENT_USER);
  await page.getByLabel('Clave').fill(RESPONDENT_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page.getByText('B. Ubicación laboral')).toBeVisible();
  const results = await new AxeBuilder({ page }).withTags(WCAG_21_AA).analyze();
  expect(results.violations).toEqual([]);
});
