import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Usuarios FICTICIOS sembrados por db/dev/data.sql, reservados para este flujo.
// Cada página se guarda una sola vez, así que cada proyecto usa su propio usuario.
const FLOW_USERS: Record<string, { user: string; password: string }> = {
  chromium: { user: 'PRUEBA05', password: 'Clave#05' },
  movil: { user: 'PRUEBA06', password: 'Clave#06' },
};
const WCAG_21_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_21_AA).analyze();
  expect(results.violations).toEqual([]);
}

async function answerYes(page: Page, question: RegExp) {
  await page.getByRole('radiogroup', { name: question }).getByRole('radio', { name: 'Sí' }).check();
}

async function saveAndContinue(page: Page) {
  await page.getByRole('button', { name: 'Guardar página' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Guardar y continuar' }).click();
}

test('el servidor público diligencia toda la encuesta y obtiene su radicado', async ({
  page,
}, testInfo) => {
  const fallback = FLOW_USERS[testInfo.project.name];
  if (!fallback) {
    throw new Error(
      `Proyecto de Playwright sin usuario de flujo asignado: ${testInfo.project.name}`,
    );
  }
  // Cada proyecto necesita su propio usuario: E2E_FLOW_USER_CHROMIUM, E2E_FLOW_USER_MOVIL, etc.
  const suffix = testInfo.project.name.toUpperCase();
  const user = process.env[`E2E_FLOW_USER_${suffix}`] ?? fallback.user;
  const password = process.env[`E2E_FLOW_PASSWORD_${suffix}`] ?? fallback.password;

  await page.goto('/ingresar');
  await page.getByLabel('Usuario').fill(user);
  await page.getByLabel('Clave').fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();

  await expect(page.getByRole('heading', { name: 'Encuesta EDIN 2026' })).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'Continuar' }),
    `La encuesta de ${user} ya avanzó: reinicie el backend para recargar db/dev/data.sql`,
  ).toBeVisible();
  await expectAccessible(page);
  await page.getByRole('link', { name: 'Continuar' }).click();

  // B1: combo de nivel y edad con rango 15–99.
  await expect(page).toHaveURL(/\/encuesta\/pagina\/B1$/);
  await page.getByRole('radiogroup', { name: /Nivel/ }).getByRole('radio').first().check();
  await page.getByLabel(/Edad en años cumplidos/).fill('14');
  await page.getByRole('button', { name: 'Guardar página' }).click();
  await expect(page.getByRole('alert')).toContainText('El valor está fuera del rango permitido.');
  await expectAccessible(page);
  await page.getByLabel(/Edad en años cumplidos/).fill('30');
  await saveAndContinue(page);

  // C1: el salto heredado deshabilita C01B al responder No.
  await expect(page).toHaveURL(/\/encuesta\/pagina\/C1$/);
  await page.getByRole('radiogroup', { name: /C01A/ }).getByRole('radio', { name: 'No' }).check();
  await expect(page.getByText('No aplica según sus respuestas anteriores.')).toBeVisible();
  await expectAccessible(page);
  await saveAndContinue(page);

  await expect(page).toHaveURL(/\/encuesta\/pagina\/C2$/);
  await answerYes(page, /C13/);
  await saveAndContinue(page);

  await expect(page).toHaveURL(/\/encuesta\/pagina\/D1$/);
  await answerYes(page, /D01A/);
  await page.getByLabel(/D05/).fill('5');
  await saveAndContinue(page);

  for (const [pageCode, question] of [
    ['D2', /D09/],
    ['E1', /E01A/],
    ['F1', /F01A/],
    ['G1', /G01A/],
    ['H1', /H01/],
  ] as const) {
    await expect(page).toHaveURL(new RegExp(`/encuesta/pagina/${pageCode}$`));
    await answerYes(page, question);
    await saveAndContinue(page);
  }

  // D18: con las 9 páginas guardadas la encuesta ya terminó; las observaciones son opcionales.
  await expect(page).toHaveURL(/\/encuesta\/terminar$/);
  const receipt = page.getByRole('status').filter({ hasText: 'Encuesta terminada' });
  await expect(receipt).toContainText(/[A-Z2-7]{5}-[A-Z2-7]{5}/);
  await page.getByLabel('Observaciones (opcional)').fill('Observación ficticia de prueba.');
  await expectAccessible(page);
  await page.getByRole('button', { name: 'Enviar observaciones' }).click();
  await expect(page.getByText('Sus observaciones ya fueron registradas.')).toBeVisible();
  await expect(receipt).toContainText(/[A-Z2-7]{5}-[A-Z2-7]{5}/);
  await expectAccessible(page);

  // Una página guardada queda en solo lectura.
  await page.goto('/encuesta/pagina/C1');
  await expect(page.getByText('Esta página ya fue guardada.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Guardar página' })).toHaveCount(0);
});
