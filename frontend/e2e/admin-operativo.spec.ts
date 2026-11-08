import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Usuarios FICTICIOS sembrados por db/dev/data.sql (Fase 4). Los roles son los de GRAL_USUARIOS_ADM.ROL:
// 3 = operativo (reportes de todo el alcance), 2 = territorial (departamentos de
// GRAL_TERRITORIAL_ACCESO: 11 y 25), 1 = ingeniero (ADMIN_NACIONAL: claves).
const OPERATIVE_USER = process.env.E2E_OPERATIVE_USER ?? 'operativo';
const OPERATIVE_PASSWORD = process.env.E2E_OPERATIVE_PASSWORD ?? 'Fixt#2026c';
const TERRITORIAL_USER = process.env.E2E_TERRITORIAL_USER ?? 'territorial-multi';
const TERRITORIAL_PASSWORD = process.env.E2E_TERRITORIAL_PASSWORD ?? 'Fixt#2026a';
const ENGINEER_USER = process.env.E2E_ENGINEER_USER ?? 'ingeniero';
const ENGINEER_PASSWORD = process.env.E2E_ENGINEER_PASSWORD ?? 'Fixt#2026d';
const WCAG_21_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

// Funcionaria ficticia de Cundinamarca (ficticio) sin novedad en data.sql; la prueba la asigna y la quita.
const DEPARTMENT_NAME = 'Cundinamarca (ficticio)';
const ENTITY_CODE = '899998';
const PERSON_NAME = 'Persona Ficticia Siete';
const OTHER_CAUSE_NOVELTY = 'Otra causa';
const OTHER_CAUSE_TEXT = 'Motivo ficticio E2E';
const KEYS_DEPARTMENT_NAME = 'Bogota D.C. (ficticio)';
const KEYS_DEPARTMENT_ROW = /Bogota D\.C\. \(ficticio\)/;

async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_21_AA).analyze();
  expect(results.violations).toEqual([]);
}

async function signIn(page: Page, username: string, password: string) {
  await page.goto('/ingresar');
  await page.getByRole('radio', { name: 'Administrador(a)' }).check();
  await page.getByLabel('Usuario').fill(username);
  await page.getByLabel('Clave').fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page.getByRole('navigation', { name: 'Administración' })).toBeVisible();
}

async function openMenuLink(page: Page, name: string) {
  await page
    .getByRole('navigation', { name: 'Administración' })
    .getByRole('link', { name, exact: true })
    .click();
}

test('el operativo abre la cobertura, cambia a EDI y exporta el Excel', async ({ page }) => {
  await signIn(page, OPERATIVE_USER, OPERATIVE_PASSWORD);
  await openMenuLink(page, 'Cobertura');
  await expect(page.getByRole('heading', { name: 'Cobertura', exact: true })).toBeVisible();
  await expect(page.getByRole('table', { name: /^Cobertura General/ })).toBeVisible();
  await expectAccessible(page);

  await page.getByRole('tab', { name: 'EDI', exact: true }).click();
  await expect(page.getByRole('tab', { name: 'EDI', exact: true })).toHaveAttribute(
    'aria-selected',
    'true',
  );
  await expect(page.getByRole('table', { name: /^Cobertura EDI \d{4}/ })).toBeVisible();
  await expectAccessible(page);

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar a Excel' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/^cobertura-edi-\d{4}-\d{4}-\d{2}-\d{2}\.xlsx$/);
});

test('el territorial asigna una novedad de otra causa, la ve en el reporte y la quita', async ({
  page,
}) => {
  test.setTimeout(90_000);
  await signIn(page, TERRITORIAL_USER, TERRITORIAL_PASSWORD);

  // Asignar.
  await openMenuLink(page, 'Gestión de novedades');
  await expect(page.getByRole('heading', { name: 'Gestión de novedades' })).toBeVisible();
  await page.getByLabel('Departamento').selectOption({ label: DEPARTMENT_NAME });
  await page.getByLabel('Entidad').selectOption(ENTITY_CODE);
  await expect(page.getByRole('row', { name: new RegExp(PERSON_NAME) })).toBeVisible();
  await expectAccessible(page);

  await page.getByRole('button', { name: `Asignar novedad de ${PERSON_NAME}` }).click();
  const editor = page.getByRole('dialog', { name: `Asignar novedad de ${PERSON_NAME}` });
  await editor.getByLabel('Novedad').selectOption({ label: OTHER_CAUSE_NOVELTY });
  await editor.getByLabel('Otra causa').fill(OTHER_CAUSE_TEXT);
  await expectAccessible(page);
  await editor.getByRole('button', { name: 'Guardar' }).click();
  await expect(editor).toBeHidden();
  const assignedRow = page.getByRole('row', { name: new RegExp(PERSON_NAME) });
  await expect(assignedRow).toContainText(OTHER_CAUSE_TEXT);

  try {
    // Verla en el reporte de novedades.
    await openMenuLink(page, 'Novedades');
    await expect(page.getByRole('heading', { name: 'Novedades', exact: true })).toBeVisible();
    const reportRow = page.getByRole('row', { name: new RegExp(PERSON_NAME) });
    await expect(reportRow).toContainText(OTHER_CAUSE_TEXT);
    await expectAccessible(page);
  } finally {
    // Quitarla (también si el reporte falla: la muestra de desarrollo debe quedar como estaba).
    await openMenuLink(page, 'Gestión de novedades');
    // Sin esperar el título, el filtro de Departamento aún sería el del reporte anterior.
    await expect(page.getByRole('heading', { name: 'Gestión de novedades' })).toBeVisible();
    await page.getByLabel('Departamento').selectOption({ label: DEPARTMENT_NAME });
    await page.getByLabel('Entidad').selectOption(ENTITY_CODE);
    await page.getByRole('button', { name: `Quitar novedad de ${PERSON_NAME}` }).click();
    await page
      .getByRole('dialog', { name: 'Quitar novedad' })
      .getByRole('button', { name: 'Quitar' })
      .click();
    await expect(
      page.getByRole('button', { name: `Asignar novedad de ${PERSON_NAME}` }),
    ).toBeVisible();
  }
});

test('el ingeniero ve el resumen de claves y descarga un departamento', async ({ page }) => {
  await signIn(page, ENGINEER_USER, ENGINEER_PASSWORD);
  await openMenuLink(page, 'Claves');
  await expect(page.getByRole('heading', { name: 'Claves de la muestra' })).toBeVisible();
  const summary = page.getByRole('table', { name: 'Claves de la muestra por departamento' });
  await expect(summary).toBeVisible();
  await expect(summary.getByRole('row', { name: KEYS_DEPARTMENT_ROW })).toBeVisible();
  await expectAccessible(page);

  const downloadPromise = page.waitForEvent('download');
  await page
    .getByRole('button', { name: `Descargar Excel de claves de ${KEYS_DEPARTMENT_NAME}` })
    .click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(
    /^claves-general-\d{4}-dpto11-\d{4}-\d{2}-\d{2}\.xlsx$/,
  );
});
