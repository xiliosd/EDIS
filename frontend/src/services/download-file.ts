/** Entrega un texto como archivo descargable sin abrir otra ventana (p. ej. un script SQL). */
export function downloadTextFile(fileName: string, content: string): void {
  downloadBlobFile(fileName, new Blob([content], { type: 'text/plain;charset=utf-8' }));
}

/** Entrega un archivo binario (p. ej. el paquete ZIP de promoción) como descarga. */
export function downloadBlobFile(fileName: string, blob: Blob): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  // Firefox solo descarga desde un enlace que está en el documento.
  document.body.append(link);
  link.click();
  link.remove();
  // Revocar en el mismo turno puede cancelar la descarga antes de que el navegador la lea.
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
