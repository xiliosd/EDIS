import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Administrador FICTICIO sembrado por db/dev/data.sql (ROL 1: ADMIN_NACIONAL + ADMIN_CUESTIONARIO).
const ADMIN_USER = process.env.E2E_ADMIN_USER ?? 'adm.nacional';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Nac#2026x';
const WCAG_21_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const SOURCE_YEAR = 2026;
const NEW_YEAR = 2027;
const SURVEY = 'EDIN';
const RULE_ID = 'c01a-uno';
const CASE_ID = 'c01a-uno-caso';
// Pregunta del cuestionario ficticio de desarrollo (otra página) que la regla pasa a ocultar.
const OTHER_TARGET = 'D01A';

// No hay vitest-axe: este es el único chequeo de accesibilidad de las pantallas de reglas.
async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_21_AA).analyze();
  expect(results.violations).toEqual([]);
}

/** Textos de las reglas que muestra la lista de la página elegida. */
async function ruleRows(page: Page): Promise<string[]> {
  return page.locator('li[id^="regla-"]').allInnerTexts();
}

test('el administrador crea una regla, la importa con ciclo, la prueba en el simulador y la cambia', async ({
  page,
}, testInfo) => {
  test.setTimeout(120_000);
  // La especificación (§9) no exige móvil para el editor de reglas: se omite, pero de forma visible.
  const skipped = testInfo.project.name !== 'chromium';
  if (skipped) {
    test.info().annotations.push({
      type: 'omitida',
      description: 'El editor de reglas solo se prueba en Chromium.',
    });
  }
  test.skip(skipped, 'El editor de reglas solo se prueba en Chromium.');

  await page.goto('/ingresar');
  await page.getByRole('radio', { name: 'Administrador(a)' }).check();
  await page.getByLabel('Usuario').fill(ADMIN_USER);
  await page.getByLabel('Clave').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.getByRole('link', { name: 'Cuestionario', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Años del cuestionario' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Publicado' })).toBeVisible();

  // 1. Reutiliza el borrador abierto (p. ej. el de admin-cuestionario en la misma corrida) o crea 2027.
  const draftRow = page
    .getByRole('row')
    .filter({ has: page.getByRole('cell', { name: 'Borrador' }) });
  let year = NEW_YEAR;
  if ((await draftRow.count()) > 0) {
    year = Number(await draftRow.getByRole('rowheader').innerText());
    await draftRow.getByRole('link').click();
  } else {
    await page.getByRole('button', { name: 'Crear año' }).click();
    const createDialog = page.getByRole('dialog', { name: 'Crear año' });
    await createDialog.getByLabel('Año nuevo').fill(String(NEW_YEAR));
    await createDialog.getByLabel('Año de origen').fill(String(SOURCE_YEAR));
    await createDialog.getByRole('button', { name: 'Crear' }).click();
  }
  await expect(page.getByRole('heading', { name: `Cuestionario ${SURVEY} ${year}` })).toBeVisible();

  const openRules = async () => {
    await page
      .getByRole('navigation', { name: 'Secciones del cuestionario' })
      .getByRole('link', { name: 'Reglas' })
      .click();
    await expect(page.getByRole('heading', { name: `Reglas ${SURVEY} ${year}` })).toBeVisible();
    await page.locator('#pagina-reglas').selectOption('C1');
  };
  const openSimulator = async () => {
    await page
      .getByRole('navigation', { name: 'Secciones del cuestionario' })
      .getByRole('link', { name: 'Simulador' })
      .click();
    await expect(page.getByRole('heading', { name: `Simulador ${SURVEY} ${year}` })).toBeVisible();
    await page.locator('#pagina-simulador').selectOption('C1');
  };

  await openRules();
  await expect(page.locator(`#regla-${RULE_ID}`)).toHaveCount(0, {
    timeout: 1_000,
  });

  // 2. Crea la regla c01a-uno (C01A = Sí → C01B) con el teclado.
  // El salto heredado (C01A = No → C01B) sigue en la lista y no interfiere con esta regla.
  const openBuilder = page.getByRole('button', { name: 'Nueva regla' });
  await openBuilder.focus();
  await page.keyboard.press('Enter');
  const builder = page.getByRole('dialog', { name: 'Nueva regla de salto' });
  await expect(builder).toBeVisible();
  await builder.getByLabel('Identificador').focus();
  await page.keyboard.type(RULE_ID);
  await builder.getByLabel('Pregunta de la condición 1').selectOption('C01A');
  await builder.getByLabel('Operador de la condición 1').selectOption('eq');
  await builder.getByLabel('Valor de la condición 1').selectOption('1');
  await builder.getByLabel('Preguntas que no se muestran').selectOption(['C01B']);
  await expect(builder.getByText('Si C01A = Sí → no se muestra C01B')).toBeVisible();
  await expectAccessible(page); // diálogo del constructor abierto
  await builder.getByRole('button', { name: 'Guardar' }).focus();
  await page.keyboard.press('Enter');
  await expect(builder).toHaveCount(0);
  const ruleItem = page.locator(`#regla-${RULE_ID}`);
  await expect(ruleItem).toContainText('Si C01A = Sí → no se muestra C01B');
  await expectAccessible(page); // reglas con datos

  // 3. Un archivo con un ciclo indirecto se rechaza con 422 y la lista no cambia.
  const rowsBefore = await ruleRows(page);
  expect(rowsBefore.length).toBeGreaterThan(1);
  const versionBefore = await page.getByText(/^Versión \d+$/).innerText();
  const exported = await page.request.get(
    `/api/v1/admin/questionnaire/${SURVEY}/${year}/rules/export`,
  );
  expect(exported.ok()).toBeTruthy();
  const file = (await exported.json()) as { saltos: unknown[] };
  // c01a-uno oculta C01B; esta regla, disparada por C01B, oculta C01A (el disparador de la anterior).
  file.saltos.push({
    id: 'c01b-oculta-c01a',
    si: { pregunta: 'C01B', op: 'eq', valor: '1' },
    entonces: { accion: 'deshabilitar', preguntas: ['C01A'] },
  });
  await page.locator('#importar-json').setInputFiles({
    name: 'reglas-con-ciclo.json',
    mimeType: 'application/json',
    buffer: Buffer.from(JSON.stringify(file)),
  });
  const importDialog = page.getByRole('dialog', { name: 'Importar reglas' });
  await importDialog.getByRole('button', { name: 'Importar' }).click();
  const rejection = page.getByRole('alert').filter({ hasText: 'No se guardó' });
  await expect(rejection).toBeVisible();
  await expect(rejection.getByText('Ciclo de saltos').first()).toBeVisible();
  await expect(rejection.getByText(/Errores nuevos/)).toBeVisible();
  await expectAccessible(page); // alerta del 422
  expect(await ruleRows(page)).toEqual(rowsBefore);
  await expect(page.locator('#regla-c01b-oculta-c01a')).toHaveCount(0);
  await expect(page.getByText(/^Versión \d+$/)).toHaveText(versionBefore);

  // 4. En el simulador, C01A = Sí oculta C01B por c01a-uno; se guarda el caso y pasa.
  await openSimulator();
  // Se ubican por la columna (campo-<COLUMNA>): otra prueba pudo cambiar el texto de la pregunta.
  await page.locator('#campo-C01A').getByRole('radio', { name: 'Sí' }).check();
  const hiddenQuestion = page
    .locator('fieldset[disabled]')
    .filter({ has: page.locator('#campo-C01B') });
  await expect(hiddenQuestion).toBeVisible();
  await expect(hiddenQuestion).toContainText(`Oculta por la regla ${RULE_ID}`);
  await expectAccessible(page); // simulador con una pregunta oculta
  await page.getByRole('button', { name: 'Guardar como caso de prueba' }).click();
  const saveDialog = page.getByRole('dialog', { name: 'Guardar como caso de prueba' });
  await saveDialog.getByLabel('Identificador del caso').fill(CASE_ID);
  await saveDialog.getByLabel('Nombre del caso').fill('C01A en Sí oculta C01B');
  await saveDialog.getByRole('button', { name: 'Guardar caso' }).click();
  const caseItem = page.locator(`#caso-${CASE_ID}`);
  await expect(caseItem).toContainText('Pasa');
  await expectAccessible(page); // lista de casos

  // 5. La regla pasa a ocultar otra pregunta: el caso falla y muestra su diferencia.
  await openRules();
  await page.getByRole('button', { name: `Editar regla ${RULE_ID}` }).click();
  const editor = page.getByRole('dialog', { name: `Editar regla ${RULE_ID}` });
  await editor.getByLabel('Preguntas que no se muestran').selectOption([OTHER_TARGET]);
  await editor.getByRole('button', { name: 'Guardar' }).click();
  await expect(editor).toHaveCount(0);
  await expect(page.locator(`#regla-${RULE_ID}`)).toContainText(
    `Si C01A = Sí → no se muestra ${OTHER_TARGET}`,
  );

  await openSimulator();
  const failedCase = page.locator(`#caso-${CASE_ID}`);
  await expect(failedCase).toContainText('Falla');
  await expect(failedCase).toContainText('Se esperaba oculta C01B y está visible.');
  await expectAccessible(page); // lista de casos con una falla
});
