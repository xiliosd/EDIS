import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Administrador FICTICIO sembrado por db/dev/data.sql (ROL 1: ADMIN_NACIONAL + ADMIN_CUESTIONARIO).
const ADMIN_USER = process.env.E2E_ADMIN_USER ?? 'adm.nacional';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Nac#2026x';
const WCAG_21_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const SOURCE_YEAR = 2026;

// La encuesta admite un solo borrador a la vez: cada proyecto crea su propio año, pero solo uno
// puede correr por cada arranque del backend (ver README, "Ambiente de pruebas").
const NEW_YEAR_BY_PROJECT: Record<string, number> = { chromium: 2027, movil: 2028 };

async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_21_AA).analyze();
  expect(results.violations).toEqual([]);
}

/** Nombres de las preguntas de una página, en el orden en que se muestran. */
async function questionLabels(page: Page, pageCode: string): Promise<string[]> {
  const list = page.getByRole('list', { name: `Preguntas de la página ${pageCode}` });
  await list.waitFor();
  const items = list.getByRole('listitem');
  const names = await items.evaluateAll((nodes) =>
    nodes.map((node) => node.getAttribute('aria-label') ?? ''),
  );
  return names.filter((name) => name.startsWith('Pregunta ')).map((name) => name.slice(9));
}

test('el administrador crea un año, edita, ordena, agrega y desactiva sin tocar 2026', async ({
  page,
}, testInfo) => {
  const newYear = NEW_YEAR_BY_PROJECT[testInfo.project.name];
  if (!newYear) {
    throw new Error(`Proyecto de Playwright sin año asignado: ${testInfo.project.name}`);
  }

  await page.goto('/ingresar');
  await page.getByRole('radio', { name: 'Administrador(a)' }).check();
  await page.getByLabel('Usuario').fill(ADMIN_USER);
  await page.getByLabel('Clave').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.getByRole('link', { name: 'Cuestionario', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Años del cuestionario' })).toBeVisible();
  // Espera la tabla (2026 publicado) antes de buscar borradores; si no, el conteo sería 0 en vano.
  await expect(page.getByRole('cell', { name: 'Publicado' })).toBeVisible();
  const openDrafts = await page.getByRole('cell', { name: 'Borrador' }).count();
  // En una corrida completa el proyecto móvil llega con el borrador del proyecto Chromium abierto
  // (solo hay un borrador por encuesta): se omite, pero de forma visible en el reporte.
  const skipReason =
    'Ya hay un borrador abierto por otro proyecto. Reinicie el backend con un RULES_DIR limpio ' +
    'y ejecute: npm run e2e:admin-movil';
  const skipped = openDrafts > 0 && testInfo.project.name !== 'chromium';
  if (skipped) {
    test.info().annotations.push({ type: 'omitida', description: skipReason });
  }
  test.skip(skipped, skipReason);
  expect(
    openDrafts,
    'Ya hay un borrador abierto: reinicie el backend y borre RULES_DIR antes de esta prueba',
  ).toBe(0);
  await expectAccessible(page);

  // Texto de la primera pregunta de C1 en 2026, para comprobar al final que no cambió.
  await page.getByRole('link', { name: String(SOURCE_YEAR) }).click();
  await expect(
    page.getByRole('heading', { name: `Cuestionario EDIN ${SOURCE_YEAR}` }),
  ).toBeVisible();
  const source = page.getByRole('region', { name: 'Página C1' });
  const firstSourceLabel = (await questionLabels(page, 'C1'))[0];
  const sourceText = await source
    .getByRole('listitem', { name: `Pregunta ${firstSourceLabel}` })
    .textContent();

  // 1. Crear el año nuevo a partir de 2026.
  await page.getByRole('link', { name: 'Volver a los años' }).click();
  await page.getByRole('button', { name: 'Crear año' }).click();
  const createDialog = page.getByRole('dialog', { name: 'Crear año' });
  await createDialog.getByLabel('Año nuevo').fill(String(newYear));
  await createDialog.getByLabel('Año de origen').fill(String(SOURCE_YEAR));
  await createDialog.getByRole('button', { name: 'Crear' }).click();
  await expect(page.getByRole('heading', { name: `Cuestionario EDIN ${newYear}` })).toBeVisible();
  await expect(page.getByText(/Estado: Borrador/)).toBeVisible();

  const chapter = page.getByRole('region', { name: 'Página C1' });
  const labels = await questionLabels(page, 'C1');
  expect(labels.length).toBeGreaterThan(1);
  const [first, second] = labels;

  // 2. Editar el texto de una pregunta de C1 y guardar.
  const editedText = `Texto de prueba E2E ${newYear}`;
  const firstItem = chapter.getByRole('listitem', { name: `Pregunta ${first}` });
  await firstItem.getByRole('button', { name: `Editar texto de ${first}` }).click();
  const field = firstItem.getByLabel(`Texto de ${first}`);
  await field.fill(editedText);
  await firstItem.getByRole('button', { name: 'Guardar' }).click();
  // El botón "Editar" solo vuelve cuando el servidor aceptó el guardado (y subió la versión).
  await expect(firstItem.getByRole('button', { name: `Editar texto de ${first}` })).toBeVisible();
  await expect(firstItem.getByText(editedText)).toBeVisible();

  // 3. Reordenar con el teclado: Enter sobre "Bajar" intercambia las dos primeras preguntas.
  await chapter.getByRole('button', { name: `Bajar ${first}` }).focus();
  await page.keyboard.press('Enter');
  await expect.poll(() => questionLabels(page, 'C1')).toEqual([second, first, ...labels.slice(2)]);
  // Si la pregunta quedó de última, "Bajar" se deshabilita y el foco pasa a "Subir".
  const focusedButton = labels.length > 2 ? 'Bajar' : 'Subir';
  await expect(chapter.getByRole('button', { name: `${focusedButton} ${first}` })).toBeFocused();

  // 4. Agregar una pregunta sin columna: queda pendiente y se descarga su script.
  // El numeral debe dar el código de la columna (ColumnCodeResolver); C30A no existe en la tabla.
  const newNumeral = 'C30A';
  const newColumn = 'C30A';
  await page.getByRole('button', { name: 'Agregar pregunta en C1' }).click();
  const addDialog = page.getByRole('dialog', { name: 'Agregar pregunta en C1' });
  await addDialog.getByLabel('Numeral').fill(newNumeral);
  await addDialog.getByLabel('Código de columna').fill(newColumn);
  await addDialog.getByLabel('Texto', { exact: true }).fill('Pregunta agregada por la prueba E2E');
  await addDialog.getByLabel('Tipo de respuesta').selectOption('TEXT');
  await addDialog.getByRole('button', { name: 'Agregar' }).click();
  const newItem = chapter.getByRole('listitem', { name: `Pregunta ${newNumeral}` });
  await expect(newItem).toBeVisible();
  await expect(newItem.getByText('Pendiente de columna')).toBeVisible();

  const downloadPromise = page.waitForEvent('download');
  await newItem
    .getByRole('button', { name: `Descargar script de columna de ${newNumeral}` })
    .click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe(`columna-${newColumn}.sql`);
  const scriptStream = await download.createReadStream();
  const chunks: Buffer[] = [];
  for await (const chunk of scriptStream) {
    chunks.push(chunk as Buffer);
  }
  expect(Buffer.concat(chunks).toString('utf8')).toContain(newColumn);

  await expectAccessible(page);

  // 5. Desactivar la pregunta nueva.
  await newItem.getByRole('button', { name: `Desactivar ${newNumeral}` }).click();
  await page
    .getByRole('dialog', { name: `Desactivar ${newNumeral}` })
    .getByRole('button', { name: 'Desactivar', exact: true })
    .click();
  await expect(newItem).toHaveCount(0);

  // 6. El árbol de 2026 es de solo lectura y su texto no cambió.
  await page.getByRole('link', { name: 'Volver a los años' }).click();
  await expect(page.getByRole('cell', { name: 'Borrador' })).toBeVisible();
  await expectAccessible(page);
  await page.getByRole('link', { name: String(SOURCE_YEAR) }).click();
  await expect(
    page.getByText(`El cuestionario de ${SOURCE_YEAR} está publicado y es de solo lectura.`),
  ).toBeVisible();
  const readOnly = page.getByRole('region', { name: 'Página C1' });
  await expect(readOnly.getByRole('button', { name: /^Editar/ })).toHaveCount(0);
  await expect(readOnly.getByRole('button', { name: /^Subir|^Bajar/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^Agregar pregunta/ })).toHaveCount(0);
  await expect(readOnly.getByRole('listitem', { name: `Pregunta ${first}` })).toHaveText(
    sourceText ?? '',
  );
  expect(await questionLabels(page, 'C1')).toEqual(labels);
  expect(sourceText ?? '').not.toContain(editedText);

  // 7. Accesibilidad del árbol de solo lectura (la del borrador y la de años se revisaron arriba).
  await expectAccessible(page);
});
