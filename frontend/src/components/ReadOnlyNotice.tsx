/** Id del aviso: los controles de escritura deshabilitados lo enlazan con aria-describedby. */
export const READ_ONLY_NOTE_ID = 'aviso-solo-lectura';

export const READ_ONLY_NOTE =
  'Este ambiente es de solo lectura: los cambios se hacen en QA y llegan con un paquete de promoción.';

interface ReadOnlyNoticeProps {
  show: boolean;
}

/** Aviso de solo lectura, una vez por página; explica por qué los controles están deshabilitados. */
export function ReadOnlyNotice({ show }: ReadOnlyNoticeProps) {
  if (!show) {
    return null;
  }
  return (
    <p
      id={READ_ONLY_NOTE_ID}
      role="note"
      className="mt-4 rounded-xl border border-ink-muted/30 bg-surface p-4 font-medium"
    >
      {READ_ONLY_NOTE}
    </p>
  );
}
