import { readFile } from 'node:fs/promises';
import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Administrador FICTICIO sembrado por db/dev/data.sql (ROL 1: ADMIN_NACIONAL + ADMIN_CUESTIONARIO).
const ADMIN_USER = process.env.E2E_ADMIN_USER ?? 'adm.nacional';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Nac#2026x';
const WCAG_21_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const SOURCE_YEAR = 2026;
const NEW_YEAR = 2027;
const SURVEY = 'EDIN';
// Caso ficticio que trae el archivo de reglas de 2026 (db/dev/rules-seed/EDIN-2026.json).
const CASE_ID = 'c01a-no-oculta-c01b';
const CORRECTION_REASON = 'Corrección de prueba ficticia';

async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_21_AA).analyze();
  expect(results.violations).toEqual([]);
}

test('el administrador publica un año, lo corrige y descarga e importa su paquete', async ({
  page,
}, testInfo) => {
  test.setTimeout(180_000);
  // La especificación (§9) no exige móvil para publicar ni promover: se omite, pero de forma visible.
  const skipped = testInfo.project.name !== 'chromium';
  if (skipped) {
    test.info().annotations.push({
      type: 'omitida',
      description: 'La publicación y los paquetes solo se prueban en Chromium.',
    });
  }
  test.skip(skipped, 'La publicación y los paquetes solo se prueban en Chromium.');

  await page.goto('/ingresar');
  await page.getByRole('radio', { name: 'Administrador(a)' }).check();
  await page.getByLabel('Usuario').fill(ADMIN_USER);
  await page.getByLabel('Clave').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.getByRole('link', { name: 'Cuestionario', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Años del cuestionario' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Publicado' }).first()).toBeVisible();

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

  const tabs = page.getByRole('navigation', { name: 'Secciones del cuestionario' });
  const openTab = async (name: string, heading: string) => {
    await tabs.getByRole('link', { name, exact: true }).click();
    await expect(page.getByRole('heading', { name: `${heading} ${SURVEY} ${year}` })).toBeVisible();
  };
  const editQuestionText = async (
    pageCode: string,
    column: string,
    text: string,
    reason?: string,
  ) => {
    await tabs.getByRole('link', { name: 'Preguntas', exact: true }).click();
    const item = page
      .getByRole('region', { name: `Página ${pageCode}` })
      .getByRole('listitem', { name: new RegExp(`^Pregunta ${column}`) });
    if (reason) {
      await page.getByLabel('Motivo de la corrección').fill(reason);
    }
    const editButton = item.getByRole('button', { name: new RegExp(`^Editar texto de ${column}`) });
    await editButton.click();
    await item.getByLabel(new RegExp(`^Texto de ${column}`)).fill(text);
    await item.getByRole('button', { name: 'Guardar' }).click();
    // El botón "Editar" vuelve solo cuando el servidor aceptó el guardado.
    await expect(editButton).toBeVisible();
    await expect(item.getByText(text)).toBeVisible();
  };
  const latestVersionRow = () => page.getByRole('rowgroup').last().getByRole('row').first();

  // Editar el texto de C01A marca por revisar el caso copiado: la lista de publicación lo señala.
  await editQuestionText('C1', 'C01A', `Texto de C01A revisado por la prueba E2E ${year}`);
  await openTab('Publicar', 'Publicar');
  const checklist = page.getByRole('region', { name: 'Resumen de la lista de publicación' });
  await expect(checklist).toContainText('La lista está incompleta');
  await expect(page.getByRole('button', { name: 'Publicar', exact: true })).toBeDisabled();
  await expectAccessible(page); // lista incompleta
  await checklist.getByRole('link', { name: `Ir al caso ${CASE_ID}` }).click();
  await expect(page.getByRole('heading', { name: `Simulador ${SURVEY} ${year}` })).toBeVisible();
  await expect(page.locator(`#caso-${CASE_ID}`)).toContainText('Por revisar');

  // Abrirlo en el simulador y guardarlo de nuevo lo deja pasando.
  await page.getByRole('button', { name: `Abrir en el simulador el caso ${CASE_ID}` }).click();
  await page.getByRole('button', { name: 'Guardar como caso de prueba' }).click();
  const saveDialog = page.getByRole('dialog', { name: 'Guardar como caso de prueba' });
  await saveDialog.getByLabel('Identificador del caso').fill(CASE_ID);
  await saveDialog.getByLabel('Nombre del caso').fill('C01A en No oculta C01B');
  await saveDialog.getByRole('button', { name: 'Guardar caso' }).click();
  await expect(page.locator(`#caso-${CASE_ID}`)).toContainText('Pasa');

  // El cuestionario de desarrollo trae B99 sin columna real (db/dev/data.sql): sin ella no se
  // puede publicar, así que se desactiva en el borrador, como lo diría la lista.
  await openTab('Preguntas', 'Cuestionario');
  const pendingColumn = page.getByRole('listitem', { name: /^Pregunta B99/ });
  if ((await pendingColumn.count()) > 0) {
    await pendingColumn.getByRole('button', { name: /^Desactivar B99/ }).click();
    await page
      .getByRole('dialog', { name: /^Desactivar B99/ })
      .getByRole('button', { name: 'Desactivar', exact: true })
      .click();
    await expect(pendingColumn).toHaveCount(0);
  }

  await openTab('Publicar', 'Publicar');
  await expect(page.getByText('La lista está completa: el año puede publicarse.')).toBeVisible();
  await page.getByRole('button', { name: 'Publicar', exact: true }).click();
  await page
    .getByRole('dialog', { name: 'Publicar el año' })
    .getByRole('button', { name: 'Confirmar publicación' })
    .click();
  await expect(page.getByText(/^Publicado el .* versión \d+$/)).toBeVisible();
  await expect(page.getByText('El año ya está publicado: solo admite correcciones.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Publicar', exact: true })).toBeDisabled();
  await expectAccessible(page); // publicado

  // 2. En un año publicado no se agregan preguntas; sí se corrige un texto, con motivo.
  await tabs.getByRole('link', { name: 'Preguntas', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Agregar pregunta en C1' })).toBeDisabled();
  await openTab('Historial', 'Historial');
  const versionBefore = Number(await latestVersionRow().getByRole('rowheader').innerText());
  // Se corrige B06, que ningún caso usa: corregir el texto de una pregunta de un caso lo dejaría
  // «por revisar» y la lista de publicación no dejaría generar el paquete hasta revisarlo.
  await editQuestionText('B1', 'B06', `Texto de B06 corregido ${year}`, CORRECTION_REASON);
  await openTab('Historial', 'Historial');
  await expect(latestVersionRow().getByRole('rowheader')).toHaveText(String(versionBefore + 1));
  await expect(latestVersionRow()).toContainText(CORRECTION_REASON);
  await expect(latestVersionRow()).toContainText('corregir-editar-texto');
  await expectAccessible(page); // historial

  // 3. Paquete de corrección: se descarga y se importa en el mismo ambiente sin tocar producción.
  await openTab('Paquetes', 'Paquetes');
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar paquete de corrección' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe(`paquete-${SURVEY}-${year}-correccion.zip`);
  const packagePath = testInfo.outputPath(download.suggestedFilename());
  await download.saveAs(packagePath);
  expect((await readFile(packagePath)).subarray(0, 2).toString('latin1')).toBe('PK');

  await page.locator('input[type="file"]').setInputFiles(packagePath);
  await page.getByRole('button', { name: 'Verificar' }).click();
  const result = page.getByRole('region', { name: 'Resultado de la verificación' });
  await expect(result).toContainText('El paquete pasó la verificación');
  await expect(result).toContainText('El paquete ya estaba importado.');
  await expect(page.getByRole('button', { name: 'Importar', exact: true })).toBeDisabled();
  await expectAccessible(page); // paquetes con resultado
});
