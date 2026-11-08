import { apiClient, ensureCsrfToken } from '../../services/api-client';
import { unwrap } from '../../services/api-error';
import type { components } from '../../services/api-schema';
import { downloadBlobFile, serverFileName } from '../../services/download-file';

export type ImportPreview = components['schemas']['ImportPreview'];
export type PackageKind = 'anio' | 'correccion';

/** Tamaño máximo del paquete; el servidor lo vuelve a comprobar con un 413. */
export const MAX_PACKAGE_BYTES = 5 * 1024 * 1024;

/** Descarga el paquete ZIP; si el servidor lo rechaza (409/422) lanza ApiError sin descargar. */
export async function downloadPackage(
  survey: string,
  year: number,
  kind: PackageKind,
): Promise<void> {
  let disposition: string | null = null;
  const request = apiClient
    .GET('/api/v1/admin/questionnaire/{encuesta}/{anio}/package', {
      params: { path: { encuesta: survey, anio: year }, query: { tipo: kind } },
      parseAs: 'blob',
    })
    .then((result) => {
      disposition = result.response.headers.get('Content-Disposition');
      return result;
    });
  const blob = (await unwrap(request)) as unknown as Blob;
  downloadBlobFile(
    serverFileName(disposition, '.zip') ?? `paquete-${survey}-${year}-${kind}.zip`,
    blob,
  );
}

/** `confirmar=false` solo revisa el paquete y muestra el impacto; `true` lo importa. */
export async function importPackage(
  survey: string,
  year: number,
  file: File,
  confirm: boolean,
): Promise<ImportPreview> {
  await ensureCsrfToken();
  return unwrap(
    apiClient.POST('/api/v1/admin/questionnaire/{encuesta}/{anio}/package/import', {
      params: { path: { encuesta: survey, anio: year }, query: { confirmar: confirm } },
      body: { archivo: '' },
      bodySerializer: () => {
        const form = new FormData();
        form.append('archivo', file);
        return form;
      },
    }),
  );
}
