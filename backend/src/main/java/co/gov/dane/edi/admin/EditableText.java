package co.gov.dane.edi.admin;

/**
 * Textos que el administrador escribe en FRM_*: cada uno con la columna real que lo recibe, de la
 * que sale su límite, y el nombre con que el mensaje de error lo menciona.
 */
public enum EditableText {
  QUESTION(FrmTable.PREGUNTA, "PREGUNTA", "El texto de la pregunta"),
  HEADER(FrmTable.ENCABEZADO, "ENCABEZADO", "El encabezado"),
  HELP_WORD(FrmTable.PRE_AYUDA, "TEXTO", "La palabra de ayuda");

  private final int maxBytes;
  private final String label;

  EditableText(FrmTable table, String column, String label) {
    this.maxBytes = table.maxBytes(column).orElseThrow();
    this.label = label;
  }

  /** Bytes de la columna; con Latin-9 es también el máximo de caracteres sin entidades. */
  public int maxBytes() {
    return maxBytes;
  }

  /**
   * El aviso menciona que lo especial ocupa más: el conteo es del texto ya guardado, donde una
   * comilla tipográfica o un emoji se vuelven un código de varios caracteres.
   */
  public String tooLongMessage() {
    return label
        + " admite hasta "
        + maxBytes
        + " caracteres (los caracteres especiales que se guardan como código cuentan como"
        + " varios).";
  }
}
