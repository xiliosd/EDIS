package co.gov.dane.edi.model;

/** Traducción de FRM_OBJETO.COD_OBJ heredado. */
public enum InputType {
  SINGLE_CHOICE,
  CHECKBOX,
  TEXT,
  UNKNOWN;

  public static InputType fromLegacyObjectCode(Integer code) {
    if (code == null) {
      return UNKNOWN;
    }
    return switch (code) {
      case 1 -> SINGLE_CHOICE;
      case 2 -> CHECKBOX;
      case 5 -> TEXT;
      default -> UNKNOWN;
    };
  }
}
