/** Entrega un texto como archivo descargable sin abrir otra ventana (p. ej. un script SQL). */
export function downloadTextFile(fileName: string, content: string): void {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/plain;charset=utf-8' }));
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
